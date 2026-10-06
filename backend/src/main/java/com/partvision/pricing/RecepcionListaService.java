package com.partvision.pricing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.domain.OrigenSincronizacion;
import com.partvision.pricing.domain.ResultadoSincronizacion;
import com.partvision.pricing.domain.SincronizacionPrecio;
import com.partvision.pricing.dto.SincronizacionResponse;
import com.partvision.pricing.repository.SincronizacionPrecioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Recibe la lista de precios de EGSA que manda el robot del cliente y la aplica con el mismo
 * motor que ADS. El pedido solo la acepta y devuelve el numero de constancia; el trabajo (leer
 * 67 mil filas y compararlas) corre en segundo plano y el robot consulta el resultado, asi no
 * depende de que el proxy aguante la espera.
 *
 * <p>Una lista que llega rara queda retenida y su archivo se guarda, porque EGSA no tiene un
 * portal para volver a bajarla: cuando una persona la revisa, "Aplicar igual" usa ese archivo.
 *
 * <p>Comparte el candado de {@link PrecioImportService}: si en ese momento corre ADS o una
 * importacion manual, el pedido recibe un 429 ("ocupado"): es el codigo que Power Automate reintenta
 * solo (reintenta 408, 429 y 5xx, nunca un 409), asi un cruce con otra carga no pierde el dia.
 */
@Slf4j
@Service
public class RecepcionListaService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EgsaRecepcionProperties props;
    private final PrecioImportService importService;
    private final AplicadorListaPrecios aplicador;
    private final SincronizacionPrecioRepository syncRepo;
    private final ListasRetenidas retenidas;
    private final FuentesListas fuentes;
    private final TransactionTemplate tx;
    private final Executor executor;

    public RecepcionListaService(EgsaRecepcionProperties props, PrecioImportService importService,
                                 AplicadorListaPrecios aplicador, SincronizacionPrecioRepository syncRepo,
                                 ListasRetenidas retenidas, FuentesListas fuentes, TransactionTemplate tx,
                                 @Qualifier("importExecutor") Executor executor) {
        this.props = props;
        this.importService = importService;
        this.aplicador = aplicador;
        this.syncRepo = syncRepo;
        this.retenidas = retenidas;
        this.fuentes = fuentes;
        this.tx = tx;
        this.executor = executor;
    }

    private FuenteLista egsa() {
        return fuentes.de(props.proveedor());
    }

    /** @param nombre nombre del archivo que dice el que lo manda; solo para el log */
    public SincronizacionResponse recibir(byte[] recibido, String nombre) {
        byte[] contenido = desenvolver(recibido);
        if (contenido == null || contenido.length < 4 || contenido[0] != 'P' || contenido[1] != 'K') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo no es un Excel (.xlsx)");
        }
        if (!importService.iniciarImport()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Hay otra actualización de precios en curso: reintentá en un par de minutos");
        }
        SincronizacionPrecio s;
        try {
            s = aplicador.nueva(egsa(), OrigenSincronizacion.RECEPCION, false);
            Long id = s.getId();
            String archivo = limpiar(nombre);
            executor.execute(() -> procesar(id, contenido, archivo, false));
            log.info("Lista de EGSA recibida: {} bytes ({}), constancia {}", contenido.length, archivo, id);
        } catch (RuntimeException e) {
            importService.terminarImport();
            throw e;
        }
        return SincronizacionResponse.from(s);
    }

    /** Lo que consulta el robot para saber como termino la lista que mando. */
    public SincronizacionResponse resultado(long id) {
        return syncRepo.findById(id)
                .filter(s -> s.getOrigen() == OrigenSincronizacion.RECEPCION
                        && s.getProveedor().equalsIgnoreCase(props.proveedor()))
                .map(SincronizacionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa recepción"));
    }

    /** "Aplicar igual": vuelve a procesar la ultima lista retenida, ya revisada por una persona. */
    public SincronizacionResponse aplicarRetenida() {
        FuenteLista f = egsa();
        byte[] contenido = retenidas.leer(f.proveedor()).orElseThrow(() -> new BusinessException(
                "No hay una lista de " + f.nombre() + " retenida para aplicar. El robot tiene que volver a mandarla."));
        if (!importService.iniciarImport()) {
            throw new BusinessException("Ya hay una importación o una actualización de precios en curso. Esperá a que termine.");
        }
        SincronizacionPrecio s;
        try {
            s = aplicador.nueva(f, OrigenSincronizacion.MANUAL, true);
            Long id = s.getId();
            executor.execute(() -> procesar(id, contenido, null, true));
        } catch (RuntimeException e) {
            importService.terminarImport();
            throw e;
        }
        return SincronizacionResponse.from(s);
    }

    /** Siempre libera el candado y siempre deja la recepcion terminada, pase lo que pase. */
    void procesar(Long id, byte[] contenido, String archivo, boolean forzar) {
        SincronizacionPrecio s = syncRepo.findById(id).orElseThrow();
        FuenteLista fuente = egsa();
        try {
            List<FilaArchivo> filas;
            try {
                filas = importService.parsearFilasExcel(contenido, props.columnaCodigo(), props.columnaPrecio());
            } catch (IllegalArgumentException e) {
                aplicador.terminarConError(s, "La lista de " + fuente.nombre() + " no tiene el formato de siempre: "
                        + e.getMessage());
                return;
            }
            tx.executeWithoutResult(st -> aplicador.aplicar(s, fuente, filas, forzar));
            if (s.getResultado() == ResultadoSincronizacion.RETENIDA) {
                retenidas.guardar(fuente.proveedor(), contenido, archivo);
            } else {
                retenidas.borrar(fuente.proveedor());
            }
        } catch (IllegalArgumentException e) {
            // Por ejemplo, que no exista la configuracion de margenes del proveedor.
            aplicador.terminarConError(s, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Fallo el procesamiento de la lista de EGSA {}", id, e);
            aplicador.terminarConError(s, "Falló la actualización por un error interno. Si se repite, avisá al soporte.");
        } finally {
            importService.terminarImport();
        }
    }

    /**
     * Power Automate, segun como se arme el paso, no manda los bytes del Excel sino el contenido
     * en base64: suelto, o dentro del objeto {@code {"$content-type": ..., "$content": "UEsDB..."}}
     * que es como representa un archivo. Se entienden las tres formas; lo que no se reconoce
     * se devuelve igual y lo rechaza la validacion de Excel.
     */
    static byte[] desenvolver(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length < 4 || (cuerpo[0] == 'P' && cuerpo[1] == 'K')) return cuerpo;
        String texto = new String(cuerpo, StandardCharsets.UTF_8).trim();
        String base64 = null;
        if (texto.startsWith("{")) {
            try {
                JsonNode contenido = JSON.readTree(texto).get("$content");
                if (contenido != null && contenido.isTextual()) base64 = contenido.asText();
            } catch (IOException e) {
                return cuerpo;
            }
        } else if (texto.startsWith("UEsD")) { // "PK" en base64
            base64 = texto;
        }
        if (base64 == null || !pareceBase64(base64)) return cuerpo;
        try {
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return cuerpo;
        }
    }

    /** El decodificador de MIME ignora en silencio lo que no es base64: aca se exige que lo sea. */
    private static boolean pareceBase64(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            boolean valido = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=' || Character.isWhitespace(c);
            if (!valido) return false;
        }
        return true;
    }

    /** El nombre lo manda quien llama: solo letras, numeros y signos comunes, y corto. */
    static String limpiar(String nombre) {
        if (nombre == null || nombre.isBlank()) return null;
        String limpio = nombre.replaceAll("[^\\p{L}\\p{N} ._()-]", "?").trim();
        return limpio.length() > 120 ? limpio.substring(0, 120) : limpio;
    }
}
