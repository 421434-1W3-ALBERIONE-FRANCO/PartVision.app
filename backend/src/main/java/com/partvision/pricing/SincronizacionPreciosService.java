package com.partvision.pricing;

import com.partvision.common.exception.BusinessException;
import com.partvision.pricing.AdsPortalClient.AdsPortalException;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.domain.*;
import com.partvision.pricing.dto.*;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import com.partvision.pricing.repository.PrecioRevisionRepository;
import com.partvision.pricing.repository.SincronizacionPrecioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.partvision.pricing.AnalizadorListaPrecios.n;

/**
 * Actualiza solos los precios de Autopartes del Sur bajando el Excel de su portal (el mismo
 * "Catalogo Autopartes del Sur" del boton "Lista de precios"), y es ademas el lugar donde se
 * consulta el estado, los avisos y las revisiones de TODAS las listas que se mantienen solas
 * (ADS y EGSA). La importacion manual de Excel sigue igual: es el respaldo.
 *
 * <p>Cada corrida deja una {@link SincronizacionPrecio} como constancia. Lo que se aplica y como
 * lo decide {@link AplicadorListaPrecios}.
 *
 * <p>Comparte el candado de {@link PrecioImportService}: nunca corre a la vez que una
 * importacion manual ni que la recepcion de otra lista.
 */
@Slf4j
@Service
public class SincronizacionPreciosService {

    private static final int HISTORIAL = 15;
    private static final int MAX_REVISIONES_LISTADAS = 1000;
    private static final String CARGA_A_MANO = "CSV_IMPORT";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final List<ResultadoSincronizacion> BUENAS =
            List.of(ResultadoSincronizacion.ACTUALIZADA, ResultadoSincronizacion.SIN_CAMBIOS);

    private final AdsSyncProperties props;
    private final AdsPortalClient portal;
    private final PrecioImportService importService;
    private final ImportPrecioBatchRepository batchRepo;
    private final SincronizacionPrecioRepository syncRepo;
    private final PrecioRevisionRepository revisionRepo;
    private final TransactionTemplate tx;
    private final Executor executor;
    private final AplicadorListaPrecios aplicador;
    private final FuentesListas fuentes;
    private final ListasRetenidas retenidas;

    public SincronizacionPreciosService(AdsSyncProperties props, AdsPortalClient portal,
                                        PrecioImportService importService, ImportPrecioBatchRepository batchRepo,
                                        SincronizacionPrecioRepository syncRepo, PrecioRevisionRepository revisionRepo,
                                        TransactionTemplate tx, @Qualifier("importExecutor") Executor executor,
                                        AplicadorListaPrecios aplicador, FuentesListas fuentes,
                                        ListasRetenidas retenidas) {
        this.props = props;
        this.portal = portal;
        this.importService = importService;
        this.batchRepo = batchRepo;
        this.syncRepo = syncRepo;
        this.revisionRepo = revisionRepo;
        this.tx = tx;
        this.executor = executor;
        this.aplicador = aplicador;
        this.fuentes = fuentes;
        this.retenidas = retenidas;
    }

    private FuenteLista ads() {
        return fuentes.de(props.proveedor());
    }

    // --- Arranque de corridas ---

    /** Una corrida que estaba en curso cuando se reinicio el servidor no va a terminar nunca. */
    @EventListener(ApplicationReadyEvent.class)
    public void cerrarInterrumpidas() {
        Integer cerradas = tx.execute(st -> syncRepo.cerrarInterrumpidas(ResultadoSincronizacion.EN_CURSO,
                ResultadoSincronizacion.ERROR, LocalDateTime.now(),
                "Se cortó porque el servidor se reinició. Volvé a correrla: en ADS con «Actualizar ahora»; "
                        + "la de EGSA la vuelve a mandar el robot."));
        if (cerradas != null && cerradas > 0) log.warn("{} actualizacion(es) de precios quedaron cortadas por un reinicio", cerradas);
    }

    @Scheduled(cron = "${partvision.precios.ads.cron:0 30 6,13 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void programada() {
        if (!props.habilitada()) {
            log.debug("Actualizacion automatica de ADS apagada: faltan las credenciales del portal");
            return;
        }
        if (!importService.iniciarImport()) {
            SincronizacionPrecio s = aplicador.nueva(ads(), OrigenSincronizacion.AUTOMATICA, false);
            aplicador.terminarConError(s, "No corrió porque había una importación de precios en curso. "
                    + "Tocá «Actualizar ahora» cuando termine.");
            return;
        }
        Long id;
        try {
            id = aplicador.nueva(ads(), OrigenSincronizacion.AUTOMATICA, false).getId();
        } catch (RuntimeException e) {
            importService.terminarImport();
            throw e;
        }
        correr(id, false);
    }

    /**
     * El boton "Actualizar ahora". Corre en segundo plano: bajar y armar la lista lleva minutos.
     *
     * @param forzar aplica aunque la lista haya llegado rara (despues de que una persona la miro)
     */
    public SincronizacionResponse iniciarManual(boolean forzar) {
        if (!props.habilitada()) {
            throw new BusinessException("La actualización automática no está configurada: falta cargar el usuario "
                    + "y la contraseña del portal de ADS en el servidor.");
        }
        if (!importService.iniciarImport()) {
            throw new BusinessException("Ya hay una importación o una actualización de precios en curso. Esperá a que termine.");
        }
        SincronizacionPrecio s;
        try {
            s = aplicador.nueva(ads(), OrigenSincronizacion.MANUAL, forzar);
            Long id = s.getId();
            executor.execute(() -> correr(id, forzar));
        } catch (RuntimeException e) {
            importService.terminarImport();
            throw e;
        }
        return SincronizacionResponse.from(s);
    }

    // --- La corrida ---

    /** Siempre libera el candado y siempre deja la corrida terminada, pase lo que pase. */
    void correr(Long id, boolean forzar) {
        SincronizacionPrecio s = syncRepo.findById(id).orElseThrow();
        FuenteLista fuente = ads();
        try {
            List<FilaArchivo> filas;
            try {
                filas = importService.parsearFilasExcel(portal.descargarListaDelCliente(),
                        props.columnaCodigo(), props.columnaPrecio());
            } catch (IllegalArgumentException e) {
                aplicador.terminarConError(s, "La lista de ADS no tiene el formato de siempre: " + e.getMessage());
                return;
            }
            tx.executeWithoutResult(st -> aplicador.aplicar(s, fuente, filas, forzar));
        } catch (AdsPortalException e) {
            aplicador.terminarConError(s, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Por ejemplo, que no exista la configuracion de margenes del proveedor.
            aplicador.terminarConError(s, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Fallo la actualizacion de precios {}", id, e);
            aplicador.terminarConError(s, "Falló la actualización por un error interno. Si se repite, avisá al soporte.");
        } finally {
            importService.terminarImport();
        }
    }

    // --- Revision de los precios que saltaron ---

    public List<PrecioRevisionResponse> revisionesPendientes() {
        return revisionesPendientes(null);
    }

    /** @param proveedor de que lista; vacio = ADS */
    public List<PrecioRevisionResponse> revisionesPendientes(String proveedor) {
        return revisionRepo.findPendientes(EstadoRevisionPrecio.PENDIENTE, fuentes.de(proveedor).proveedor(),
                        PageRequest.of(0, MAX_REVISIONES_LISTADAS))
                .stream().map(PrecioRevisionResponse::from).toList();
    }

    /** Aprueba los precios: se aplican con la configuracion de margenes de este momento. */
    public RevisionPreciosResponse aplicarRevisiones(List<Long> ids) {
        return conCandado(() -> tx.execute(st -> aplicador.aplicarRevisiones(pendientes(ids))));
    }

    public RevisionPreciosResponse descartarRevisiones(List<Long> ids) {
        return conCandado(() -> tx.execute(st -> aplicador.descartarRevisiones(pendientes(ids))));
    }

    /** Siempre por id: un pedido vacio por error no puede terminar aplicando o descartando todo. */
    private List<PrecioRevision> pendientes(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("Elegí al menos un precio.");
        return revisionRepo.findConProductoByIdIn(ids).stream()
                .filter(r -> r.getEstado() == EstadoRevisionPrecio.PENDIENTE).toList();
    }

    /** Aprobar o descartar mientras corre una actualizacion pisaria lo que ella esta decidiendo. */
    private <T> T conCandado(java.util.function.Supplier<T> accion) {
        if (!importService.iniciarImport()) {
            throw new BusinessException("Hay una importación o una actualización de precios en curso. Probá en un rato.");
        }
        try {
            return accion.get();
        } finally {
            importService.terminarImport();
        }
    }

    // --- Estado y avisos ---

    public SincronizacionEstadoResponse estado() {
        return estado(null);
    }

    /** @param proveedor de que lista; vacio = ADS */
    public SincronizacionEstadoResponse estado(String proveedor) {
        FuenteLista f = fuentes.de(proveedor);
        List<SincronizacionPrecio> historial = syncRepo.findByProveedorOrderByIniciadaEnDesc(f.proveedor(),
                PageRequest.of(0, HISTORIAL));
        SincronizacionPrecio ultima = historial.isEmpty() ? null : historial.getFirst();
        LocalDateTime ultimaOk = ultimaActualizacion(f);
        long pendientes = revisionRepo.contarPorEstado(EstadoRevisionPrecio.PENDIENTE, f.proveedor());
        return new SincronizacionEstadoResponse(
                f.habilitada(),
                f.proveedor(),
                f.recibeArchivo(),
                ultima != null && ultima.getResultado() == ResultadoSincronizacion.EN_CURSO,
                ultima == null ? null : SincronizacionResponse.from(ultima),
                ultimaOk,
                pendientes,
                alerta(f, historial, ultimaOk, pendientes),
                historial.stream().map(SincronizacionResponse::from).toList(),
                f.recibeArchivo() && retenidas.hay(f.proveedor()));
    }

    /**
     * El aviso para la barra de arriba, juntando todas las listas; vacio si no hay nada que mirar.
     * Si hay errores, solo se muestran esos: lo grave tapa lo que es para revisar.
     */
    public Optional<AlertaPreciosResponse> alerta() {
        List<AlertaPreciosResponse> alertas = new ArrayList<>();
        for (FuenteLista f : fuentes.todas()) {
            List<SincronizacionPrecio> recientes = syncRepo.findByProveedorOrderByIniciadaEnDesc(f.proveedor(),
                    PageRequest.of(0, 2));
            long pendientes = revisionRepo.contarPorEstado(EstadoRevisionPrecio.PENDIENTE, f.proveedor());
            AlertaPreciosResponse a = alerta(f, recientes, ultimaActualizacion(f), pendientes);
            if (a != null) alertas.add(a);
        }
        if (alertas.isEmpty()) return Optional.empty();
        String nivel = alertas.stream().anyMatch(a -> "ERROR".equals(a.nivel())) ? "ERROR" : "AVISO";
        return Optional.of(new AlertaPreciosResponse(nivel, alertas.stream()
                .filter(a -> nivel.equals(a.nivel())).map(AlertaPreciosResponse::mensaje)
                .collect(Collectors.joining(" · "))));
    }

    /**
     * Cuando los precios de esta lista quedaron al dia por ultima vez: la ultima corrida buena
     * o la ultima carga hecha a mano (el robot de EGSA hoy carga por la pantalla), lo que sea mas nuevo.
     */
    private LocalDateTime ultimaActualizacion(FuenteLista f) {
        LocalDateTime sync = syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(f.proveedor(), BUENAS)
                .map(SincronizacionPrecio::getIniciadaEn).orElse(null);
        LocalDateTime manual = batchRepo.findFirstByProveedorIgnoreCaseAndFuenteAndEstadoOrderByCreatedAtDesc(
                        f.proveedor(), CARGA_A_MANO, "APLICADO")
                .map(com.partvision.pricing.domain.ImportPrecioBatch::getCreatedAt).orElse(null);
        if (sync == null) return manual;
        return manual != null && manual.isAfter(sync) ? manual : sync;
    }

    private AlertaPreciosResponse alerta(FuenteLista f, List<SincronizacionPrecio> recientes, LocalDateTime ultimaOk,
                                         long pendientes) {
        // ADS sin credenciales: la funcion esta apagada, no hay nada que avisar.
        if (!f.recibeArchivo() && !f.habilitada()) return null;
        // Mientras corre una, lo que vale es la anterior.
        SincronizacionPrecio ultima = recientes.isEmpty() ? null : recientes.getFirst();
        if (ultima != null && ultima.getResultado() == ResultadoSincronizacion.EN_CURSO) {
            ultima = recientes.size() < 2 ? null : recientes.get(1);
        }
        // Sin ninguna buena todavia (recien activada) no hay "desde cuando": vale el motivo de la ultima.
        if (ultimaOk != null && ultimaOk.isBefore(LocalDateTime.now().minusDays(f.diasSinActualizar()))) {
            String motivo = ultima == null || ultima.getMensaje() == null ? "" : " " + ultima.getMensaje();
            return AlertaPreciosResponse.error("Los precios de " + f.proveedor() + " no se actualizan desde el "
                    + ultimaOk.format(FECHA) + "." + motivo);
        }
        if (ultima == null) return null;
        if (ultima.getResultado() == ResultadoSincronizacion.ERROR) {
            return AlertaPreciosResponse.error("No se pudieron actualizar los precios de " + f.proveedor()
                    + ": " + ultima.getMensaje());
        }
        if (ultima.getResultado() == ResultadoSincronizacion.RETENIDA) {
            return AlertaPreciosResponse.aviso("La lista de precios de " + f.proveedor()
                    + " llegó con datos raros y no se aplicó. Revisala en Precios.");
        }
        if (pendientes > 0) {
            return AlertaPreciosResponse.aviso(n(pendientes) + " precio(s) de " + f.proveedor()
                    + " cambiaron más de lo normal y esperan tu revisión en Precios.");
        }
        return null;
    }
}
