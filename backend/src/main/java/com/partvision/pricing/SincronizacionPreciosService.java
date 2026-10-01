package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.pricing.AdsPortalClient.AdsPortalException;
import com.partvision.pricing.AnalizadorListaPrecios.Analisis;
import com.partvision.pricing.AnalizadorListaPrecios.Cambio;
import com.partvision.pricing.AnalizadorListaPrecios.Referencia;
import com.partvision.pricing.AnalizadorListaPrecios.Salto;
import com.partvision.pricing.AnalizadorListaPrecios.Umbrales;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.Tarifa;
import com.partvision.pricing.domain.*;
import com.partvision.pricing.dto.*;
import com.partvision.pricing.repository.HistorialPrecioRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.partvision.pricing.AnalizadorListaPrecios.n;

/**
 * Actualiza solos los precios de Autopartes del Sur bajando el Excel de su portal (el mismo
 * "Catalogo Autopartes del Sur" del boton "Lista de precios"). La importacion manual sigue igual: es el
 * respaldo para cuando el portal no responde.
 *
 * <p>Cada corrida deja una {@link SincronizacionPrecio} como constancia. Se aplica solo lo que
 * cambio (sin reescribir 67 mil precios iguales en el historial), lo que salta demasiado de
 * golpe queda en {@link PrecioRevision} para una persona, y si la lista entera viene rara no se
 * aplica nada ({@code RETENIDA}) hasta que alguien toque "Aplicar igual".
 *
 * <p>Comparte el candado de {@link PrecioImportService}: nunca corre a la vez que una
 * importacion manual.
 */
@Slf4j
@Service
public class SincronizacionPreciosService {

    static final String FUENTE = "API_SYNC";
    private static final int HISTORIAL = 15;
    private static final int MAX_REVISIONES_LISTADAS = 1000;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final List<ResultadoSincronizacion> BUENAS =
            List.of(ResultadoSincronizacion.ACTUALIZADA, ResultadoSincronizacion.SIN_CAMBIOS);

    private final AdsSyncProperties props;
    private final AdsPortalClient portal;
    private final PrecioImportService importService;
    private final ProductoRepository productoRepository;
    private final ImportPrecioBatchRepository batchRepo;
    private final HistorialPrecioRepository historialRepo;
    private final SincronizacionPrecioRepository syncRepo;
    private final PrecioRevisionRepository revisionRepo;
    private final TransactionTemplate tx;
    private final Executor executor;

    public SincronizacionPreciosService(AdsSyncProperties props, AdsPortalClient portal,
                                        PrecioImportService importService, ProductoRepository productoRepository,
                                        ImportPrecioBatchRepository batchRepo, HistorialPrecioRepository historialRepo,
                                        SincronizacionPrecioRepository syncRepo, PrecioRevisionRepository revisionRepo,
                                        TransactionTemplate tx, @Qualifier("importExecutor") Executor executor) {
        this.props = props;
        this.portal = portal;
        this.importService = importService;
        this.productoRepository = productoRepository;
        this.batchRepo = batchRepo;
        this.historialRepo = historialRepo;
        this.syncRepo = syncRepo;
        this.revisionRepo = revisionRepo;
        this.tx = tx;
        this.executor = executor;
    }

    // --- Arranque de corridas ---

    /** Una corrida que estaba en curso cuando se reinicio el servidor no va a terminar nunca. */
    @EventListener(ApplicationReadyEvent.class)
    public void cerrarInterrumpidas() {
        Integer cerradas = tx.execute(st -> syncRepo.cerrarInterrumpidas(ResultadoSincronizacion.EN_CURSO,
                ResultadoSincronizacion.ERROR, LocalDateTime.now(),
                "Se cortó porque el servidor se reinició. Tocá «Actualizar ahora» para volver a correrla."));
        if (cerradas != null && cerradas > 0) log.warn("{} actualizacion(es) de precios quedaron cortadas por un reinicio", cerradas);
    }

    @Scheduled(cron = "${partvision.precios.ads.cron:0 0 7,13 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void programada() {
        if (!props.habilitada()) {
            log.debug("Actualizacion automatica de ADS apagada: faltan las credenciales del portal");
            return;
        }
        if (!importService.iniciarImport()) {
            SincronizacionPrecio s = nueva(OrigenSincronizacion.AUTOMATICA, false);
            terminarConError(s, "No corrió porque había una importación de precios en curso. "
                    + "Tocá «Actualizar ahora» cuando termine.");
            return;
        }
        Long id;
        try {
            id = nueva(OrigenSincronizacion.AUTOMATICA, false).getId();
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
            s = nueva(OrigenSincronizacion.MANUAL, forzar);
            Long id = s.getId();
            executor.execute(() -> correr(id, forzar));
        } catch (RuntimeException e) {
            importService.terminarImport();
            throw e;
        }
        return SincronizacionResponse.from(s);
    }

    private SincronizacionPrecio nueva(OrigenSincronizacion origen, boolean forzar) {
        SincronizacionPrecio s = new SincronizacionPrecio();
        s.setProveedor(props.proveedor());
        s.setOrigen(origen);
        s.setForzada(forzar);
        return syncRepo.save(s);
    }

    // --- La corrida ---

    /** Siempre libera el candado y siempre deja la corrida terminada, pase lo que pase. */
    void correr(Long id, boolean forzar) {
        SincronizacionPrecio s = syncRepo.findById(id).orElseThrow();
        try {
            List<FilaArchivo> filas;
            try {
                filas = importService.parsearFilasExcel(portal.descargarListaDelCliente(),
                        props.columnaCodigo(), props.columnaPrecio());
            } catch (IllegalArgumentException e) {
                terminarConError(s, "La lista de ADS no tiene el formato de siempre: " + e.getMessage());
                return;
            }
            tx.executeWithoutResult(st -> analizarYAplicar(s, filas, forzar));
        } catch (AdsPortalException e) {
            terminarConError(s, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Por ejemplo, que no exista la configuracion de margenes del proveedor.
            terminarConError(s, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Fallo la actualizacion de precios {}", id, e);
            terminarConError(s, "Falló la actualización por un error interno. Si se repite, avisá al soporte.");
        } finally {
            importService.terminarImport();
        }
    }

    private void analizarYAplicar(SincronizacionPrecio s, List<FilaArchivo> filas, boolean forzar) {
        Tarifa tarifa = importService.obtenerTarifa(props.proveedor());
        Referencia ref = syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(props.proveedor(), BUENAS)
                .map(r -> new Referencia(r.getFilasLista()))
                .orElse(Referencia.NINGUNA);
        Set<String> skus = filas.stream().map(FilaArchivo::sku).filter(Objects::nonNull).map(String::trim)
                .filter(x -> !x.isEmpty()).collect(Collectors.toSet());
        Map<String, List<Producto>> productos = importService.buscarProductosPorSkuEnLotes(skus);

        Analisis a = AnalizadorListaPrecios.analizar(filas, productos, tarifa, props.proveedor(),
                Umbrales.de(props), ref, importService::parsearPrecio);

        s.setFilasLista(a.filasLista());
        s.setSinCambio(a.sinCambio());
        s.setNoEncontrados(a.noEncontrados());
        s.setFilasInvalidas(a.invalidas());

        List<String> problemas = new ArrayList<>(a.motivosParaFrenar());
        problemas.addAll(a.problemas());
        s.setProblemas(String.join("\n", problemas));

        if (a.hayQueFrenar() && !forzar) {
            s.setResultado(ResultadoSincronizacion.RETENIDA);
            s.setMensaje("No se aplicó la lista de ADS porque llegó con datos raros. Revisá los motivos y, "
                    + "si está todo bien, tocá «Aplicar igual».");
            terminar(s);
            log.warn("Lista de ADS retenida: {}", a.motivosParaFrenar());
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (!a.cambios().isEmpty()) {
            ImportPrecioBatch batch = nuevoBatch(s.getOrigen() == OrigenSincronizacion.AUTOMATICA
                    ? "Actualización automática de ADS" : "Actualización de ADS (botón)");
            List<HistorialPrecio> historial = new ArrayList<>(a.cambios().size());
            for (Cambio c : a.cambios()) {
                historial.add(registrarCambio(c.producto(), c.costo(), c.venta(), tarifa, batch, ahora));
            }
            productoRepository.saveAll(a.cambios().stream().map(Cambio::producto).toList());
            historialRepo.saveAll(historial);
            cerrarBatch(batch, a.filasLista(), a.cambios().size(),
                    a.sinCambio() + a.noEncontrados() + a.saltos().size(), a.repetidos());
            s.setBatchId(batch.getId());
        }

        // Lo que habia quedado pendiente lo reemplaza lo que dice la lista de hoy.
        revisionRepo.vencerPendientes(EstadoRevisionPrecio.PENDIENTE, EstadoRevisionPrecio.VENCIDA, props.proveedor(), ahora);
        if (!a.saltos().isEmpty()) {
            List<PrecioRevision> revisiones = new ArrayList<>(a.saltos().size());
            for (Salto salto : a.saltos()) {
                PrecioRevision r = new PrecioRevision();
                r.setSincronizacionId(s.getId());
                r.setProducto(salto.producto());
                r.setPrecioLista(salto.precioLista());
                r.setCostoActual(salto.producto().getPrecioCosto());
                r.setCostoNuevo(salto.costo());
                r.setVariacionPct(salto.variacionPct());
                revisiones.add(r);
            }
            revisionRepo.saveAll(revisiones);
        }

        s.setActualizados(a.cambios().size());
        s.setEnRevision(a.saltos().size());
        s.setResultado(a.cambios().isEmpty() ? ResultadoSincronizacion.SIN_CAMBIOS : ResultadoSincronizacion.ACTUALIZADA);
        String mensaje = a.cambios().isEmpty()
                ? "La lista de ADS no trae precios distintos a los que ya tenés."
                : "Se actualizaron " + n(a.cambios().size()) + " precio(s) de " + props.proveedor() + ".";
        if (!a.saltos().isEmpty()) mensaje += " " + n(a.saltos().size()) + " esperan tu revisión.";
        if (forzar && a.hayQueFrenar()) mensaje += " Se aplicó a pedido tuyo, aunque la lista llegó con datos raros.";
        s.setMensaje(mensaje);
        terminar(s);
        log.info("Actualizacion de precios {}: {} ({} sin cambio, {} para revisar)", s.getId(), s.getResultado(),
                a.sinCambio(), a.saltos().size());
    }

    private ImportPrecioBatch nuevoBatch(String archivo) {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setProveedor(props.proveedor());
        batch.setFuente(FUENTE);
        batch.setArchivo(archivo);
        return batchRepo.save(batch);
    }

    private void cerrarBatch(ImportPrecioBatch batch, int total, int aplicados, int omitidos, int conflictos) {
        batch.setTotal(total);
        batch.setAplicados(aplicados);
        batch.setOmitidos(omitidos);
        batch.setConflictos(conflictos);
        batchRepo.save(batch);
    }

    /** Igual que la importacion manual: el historial es lo que permite revertir el batch. */
    private HistorialPrecio registrarCambio(Producto p, BigDecimal costo, BigDecimal venta, Tarifa tarifa,
                                            ImportPrecioBatch batch, LocalDateTime ahora) {
        HistorialPrecio h = new HistorialPrecio();
        h.setProducto(p);
        h.setBatch(batch);
        h.setPrecioCostoAnterior(p.getPrecioCosto());
        h.setPrecioVentaAnterior(p.getPrecioVenta());
        h.setPrecioCostoNuevo(costo);
        h.setPrecioVentaNuevo(venta);
        h.setMargenAplicado(tarifa.margen());
        p.setPrecioCosto(costo);
        p.setPrecioVenta(venta);
        p.setPrecioActualizadoEn(ahora);
        return h;
    }

    private void terminarConError(SincronizacionPrecio s, String mensaje) {
        s.setResultado(ResultadoSincronizacion.ERROR);
        s.setMensaje(mensaje);
        terminar(s);
        log.warn("Actualizacion de precios {} fallo: {}", s.getId(), mensaje);
    }

    private void terminar(SincronizacionPrecio s) {
        s.setTerminadaEn(LocalDateTime.now());
        syncRepo.save(s);
    }

    // --- Revision de los precios que saltaron ---

    public List<PrecioRevisionResponse> revisionesPendientes() {
        return revisionRepo.findPendientes(EstadoRevisionPrecio.PENDIENTE, props.proveedor(),
                        PageRequest.of(0, MAX_REVISIONES_LISTADAS))
                .stream().map(PrecioRevisionResponse::from).toList();
    }

    /** Aprueba los precios: se aplican con la configuracion de margenes de este momento. */
    public RevisionPreciosResponse aplicarRevisiones(List<Long> ids) {
        return conCandado(() -> tx.execute(st -> {
            List<PrecioRevision> pendientes = pendientes(ids);
            if (pendientes.isEmpty()) return new RevisionPreciosResponse(0, null, "No había precios pendientes para aplicar.");

            Tarifa tarifa = importService.obtenerTarifa(props.proveedor());
            LocalDateTime ahora = LocalDateTime.now();
            ImportPrecioBatch batch = nuevoBatch("Revisión de precios de ADS");
            List<HistorialPrecio> historial = new ArrayList<>();
            for (PrecioRevision r : pendientes) {
                BigDecimal costo = tarifa.costoDesde(r.getPrecioLista());
                historial.add(registrarCambio(r.getProducto(), costo, tarifa.ventaDesde(costo), tarifa, batch, ahora));
                r.setEstado(EstadoRevisionPrecio.APLICADA);
                r.setResueltaEn(ahora);
            }
            productoRepository.saveAll(pendientes.stream().map(PrecioRevision::getProducto).toList());
            historialRepo.saveAll(historial);
            revisionRepo.saveAll(pendientes);
            cerrarBatch(batch, pendientes.size(), pendientes.size(), 0, 0);
            return new RevisionPreciosResponse(pendientes.size(), batch.getId(),
                    "Se aplicaron " + n(pendientes.size()) + " precio(s). Si te equivocaste, podés revertirlo desde el historial.");
        }));
    }

    public RevisionPreciosResponse descartarRevisiones(List<Long> ids) {
        return conCandado(() -> tx.execute(st -> {
            List<PrecioRevision> pendientes = pendientes(ids);
            LocalDateTime ahora = LocalDateTime.now();
            pendientes.forEach(r -> {
                r.setEstado(EstadoRevisionPrecio.DESCARTADA);
                r.setResueltaEn(ahora);
            });
            revisionRepo.saveAll(pendientes);
            return new RevisionPreciosResponse(pendientes.size(), null, pendientes.isEmpty()
                    ? "No había precios pendientes para descartar."
                    : "Se descartaron " + n(pendientes.size()) + " precio(s): quedan como estaban. "
                      + "Si ADS los sigue publicando así, vuelven a aparecer en la próxima actualización.");
        }));
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
        List<SincronizacionPrecio> historial = syncRepo.findByProveedorOrderByIniciadaEnDesc(props.proveedor(),
                PageRequest.of(0, HISTORIAL));
        SincronizacionPrecio ultima = historial.isEmpty() ? null : historial.getFirst();
        Optional<SincronizacionPrecio> buena = syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(
                props.proveedor(), BUENAS);
        long pendientes = revisionRepo.contarPorEstado(EstadoRevisionPrecio.PENDIENTE, props.proveedor());
        return new SincronizacionEstadoResponse(
                props.habilitada(),
                props.proveedor(),
                ultima != null && ultima.getResultado() == ResultadoSincronizacion.EN_CURSO,
                ultima == null ? null : SincronizacionResponse.from(ultima),
                buena.map(SincronizacionPrecio::getIniciadaEn).orElse(null),
                pendientes,
                alerta(historial, buena.orElse(null), pendientes),
                historial.stream().map(SincronizacionResponse::from).toList());
    }

    /** El aviso para la barra de arriba; vacio si no hay nada que mirar. */
    public Optional<AlertaPreciosResponse> alerta() {
        List<SincronizacionPrecio> recientes = syncRepo.findByProveedorOrderByIniciadaEnDesc(props.proveedor(),
                PageRequest.of(0, 2));
        SincronizacionPrecio buena = syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(
                props.proveedor(), BUENAS).orElse(null);
        long pendientes = revisionRepo.contarPorEstado(EstadoRevisionPrecio.PENDIENTE, props.proveedor());
        return Optional.ofNullable(alerta(recientes, buena, pendientes));
    }

    private AlertaPreciosResponse alerta(List<SincronizacionPrecio> recientes, SincronizacionPrecio buena, long pendientes) {
        if (!props.habilitada() || recientes.isEmpty()) return null;
        // Mientras corre una, lo que vale es la anterior.
        SincronizacionPrecio ultima = recientes.getFirst();
        if (ultima.getResultado() == ResultadoSincronizacion.EN_CURSO) {
            if (recientes.size() < 2) return null;
            ultima = recientes.get(1);
        }
        // Sin ninguna buena todavia (recien activada) no hay "desde cuando": vale el motivo de la ultima.
        LocalDateTime limite = LocalDateTime.now().minusDays(props.diasSinActualizar());
        if (buena != null && buena.getIniciadaEn().isBefore(limite)) {
            return AlertaPreciosResponse.error("Los precios de " + props.proveedor() + " no se actualizan desde el "
                    + buena.getIniciadaEn().format(FECHA) + ". " + ultima.getMensaje());
        }
        if (ultima.getResultado() == ResultadoSincronizacion.ERROR) {
            return AlertaPreciosResponse.error("No se pudieron actualizar los precios de " + props.proveedor()
                    + ": " + ultima.getMensaje());
        }
        if (ultima.getResultado() == ResultadoSincronizacion.RETENIDA) {
            return AlertaPreciosResponse.aviso("La lista de precios de " + props.proveedor()
                    + " llegó con datos raros y no se aplicó. Revisala en Precios.");
        }
        if (pendientes > 0) {
            return AlertaPreciosResponse.aviso(n(pendientes) + " precio(s) de " + props.proveedor()
                    + " cambiaron más de lo normal y esperan tu revisión en Precios.");
        }
        return null;
    }
}
