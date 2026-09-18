package com.partvision.compras;

import com.partvision.common.exception.BusinessException;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.dto.FilaSheetRequest;
import com.partvision.compras.dto.RecepcionFilasResponse;
import com.partvision.compras.dto.RecepcionFilasResponse.FilaIgnorada;
import com.partvision.compras.dto.RecepcionFilasResponse.ResultadoFactura;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recibe la tabla "Ingreso stock" del cliente tal como la lee Power Automate: una fila por
 * producto, con el numero de factura repetido. Agrupa por factura y la registra o la pone al
 * dia con {@link CompraService#sincronizar}.
 *
 * <p>No es transaccional a proposito: cada factura se guarda en su propia transaccion (la
 * llamada pasa por el proxy de {@code CompraService}), asi una factura con problemas no
 * frena a las demas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecepcionFilasService {

    private final CompraService compraService;
    private final ProveedorResolver proveedores;

    public RecepcionFilasResponse recibir(List<FilaSheetRequest> filas) {
        List<FilaIgnorada> ignoradas = new ArrayList<>();
        Map<String, List<FilaSheetRequest>> porFactura = new LinkedHashMap<>();

        for (int i = 0; i < filas.size(); i++) {
            FilaSheetRequest fila = filas.get(i);
            String numero = LectorSheet.limpio(fila.factura());
            String descripcion = LectorSheet.limpio(fila.descripcion());
            if (numero == null) {
                ignoradas.add(new FilaIgnorada(i + 1, null, descripcion, "sin numero de factura"));
            } else if (LectorSheet.cantidad(fila.cantidad()) == null) {
                // Anotaciones intercaladas entre los productos, como "CONTROLO:".
                ignoradas.add(new FilaIgnorada(i + 1, numero, descripcion,
                        "sin cantidad (o no es un entero positivo)"));
            } else {
                porFactura.computeIfAbsent(numero, k -> new ArrayList<>()).add(fila);
            }
        }

        List<ResultadoFactura> resultados = new ArrayList<>();
        porFactura.forEach((numero, filasFactura) -> resultados.add(procesar(numero, filasFactura)));

        RecepcionFilasResponse respuesta = RecepcionFilasResponse.de(filas.size(), resultados, ignoradas);
        log.info("Planilla recibida: {} filas, {} facturas ({} nuevas, {} actualizadas, {} conflictos, {} errores)",
                respuesta.filasRecibidas(), respuesta.facturas(), respuesta.creadas(),
                respuesta.actualizadas(), respuesta.conflictos(), respuesta.errores());
        return respuesta;
    }

    private ResultadoFactura procesar(String numero, List<FilaSheetRequest> filas) {
        try {
            ResultadoSincronizacion r = compraService.sincronizar(armar(numero, filas));
            String mensaje = r.mensaje();
            if (estadoMixto(filas)) {
                mensaje += ". Hay filas INGRESADA y filas EN TRANSITO: queda en transito hasta que"
                        + " todas digan INGRESADA";
            }
            return new ResultadoFactura(numero, r.tipo().name(), r.compra().getEstado().name(),
                    r.lineas(), r.lineasMatcheadas(), mensaje);
        } catch (BusinessException e) {
            return new ResultadoFactura(numero, "ERROR", null, null, null, e.getMessage());
        } catch (RuntimeException e) {
            // Una falla inesperada no puede tirar el resto de la planilla. El detalle va al log.
            log.error("Error inesperado al recibir la factura {}", numero, e);
            return new ResultadoFactura(numero, "ERROR", null, null, null,
                    "Error interno al procesar la factura");
        }
    }

    /**
     * Las filas de una factura comparten fecha, proveedor y estado. Si no coinciden no se
     * adivina: la factura vuelve con error y el resto de la planilla sigue.
     */
    private FacturaEntrante armar(String numero, List<FilaSheetRequest> filas) {
        Set<LocalDate> fechas = new LinkedHashSet<>();
        for (FilaSheetRequest fila : filas) {
            if (LectorSheet.limpio(fila.fechaFactura()) != null) {
                fechas.add(LectorSheet.fecha(fila.fechaFactura()));
            }
        }
        if (fechas.isEmpty()) {
            throw new BusinessException("Falta la fecha de la factura");
        }
        if (fechas.size() > 1) {
            throw new BusinessException("Las filas de la factura tienen fechas distintas: " + fechas);
        }

        Set<String> proveedoresFactura = new LinkedHashSet<>();
        for (FilaSheetRequest fila : filas) {
            String proveedor = proveedores.resolver(fila.proveedor());
            if (proveedor != null) {
                proveedoresFactura.add(proveedor);
            }
        }
        if (proveedoresFactura.size() > 1) {
            throw new BusinessException(
                    "Las filas de la factura tienen proveedores distintos: " + proveedoresFactura);
        }

        boolean todasIngresadas = filas.stream().allMatch(f -> LectorSheet.dicenIngresada(f.estatus()));
        List<FacturaEntrante.Linea> lineas = filas.stream()
                .map(f -> new FacturaEntrante.Linea(
                        LectorSheet.codigo(f.codigo()),
                        LectorSheet.limpio(f.descripcion()),
                        Objects.requireNonNull(LectorSheet.cantidad(f.cantidad()))))
                .toList();

        return new FacturaEntrante(
                numero,
                fechas.iterator().next(),
                proveedoresFactura.isEmpty() ? null : proveedoresFactura.iterator().next(),
                todasIngresadas ? CompraEstado.POR_UBICAR : CompraEstado.EN_TRANSITO,
                lineas);
    }

    private static boolean estadoMixto(List<FilaSheetRequest> filas) {
        boolean alguna = filas.stream().anyMatch(f -> LectorSheet.dicenIngresada(f.estatus()));
        boolean todas = filas.stream().allMatch(f -> LectorSheet.dicenIngresada(f.estatus()));
        return alguna && !todas;
    }
}
