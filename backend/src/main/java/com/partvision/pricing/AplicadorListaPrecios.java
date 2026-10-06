package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.imports.service.ProductoImporter;
import com.partvision.pricing.AnalizadorListaPrecios.Analisis;
import com.partvision.pricing.AnalizadorListaPrecios.Cambio;
import com.partvision.pricing.AnalizadorListaPrecios.Nuevo;
import com.partvision.pricing.AnalizadorListaPrecios.Referencia;
import com.partvision.pricing.AnalizadorListaPrecios.Salto;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.Tarifa;
import com.partvision.pricing.domain.*;
import com.partvision.pricing.dto.RevisionPreciosResponse;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import com.partvision.pricing.repository.PrecioRevisionRepository;
import com.partvision.pricing.repository.SincronizacionPrecioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.partvision.pricing.AnalizadorListaPrecios.ejemplos;
import static com.partvision.pricing.AnalizadorListaPrecios.n;

/**
 * Aplica una lista de precios ya leida, sea de ADS (la baja el servidor) o de EGSA (la manda el
 * robot del cliente): analiza, da de alta los codigos nuevos si la fuente lo permite, escribe
 * solo lo que cambio con su historial, manda a revision lo que salta de golpe y deja la
 * constancia en la {@link SincronizacionPrecio}.
 *
 * <p>Todo lo que escribe va en la transaccion del que llama: si algo falla, no queda nada a medias.
 */
@Slf4j
@Service
class AplicadorListaPrecios {

    private static final List<ResultadoSincronizacion> BUENAS =
            List.of(ResultadoSincronizacion.ACTUALIZADA, ResultadoSincronizacion.SIN_CAMBIOS);

    private final PrecioImportService importService;
    private final ProductoRepository productoRepository;
    private final ProductoBulkImporter bulkImporter;
    private final ImportPrecioBatchRepository batchRepo;
    private final HistorialPrecioRepository historialRepo;
    private final SincronizacionPrecioRepository syncRepo;
    private final PrecioRevisionRepository revisionRepo;
    private final FuentesListas fuentes;

    AplicadorListaPrecios(PrecioImportService importService, ProductoRepository productoRepository,
                          ProductoBulkImporter bulkImporter, ImportPrecioBatchRepository batchRepo,
                          HistorialPrecioRepository historialRepo, SincronizacionPrecioRepository syncRepo,
                          PrecioRevisionRepository revisionRepo, FuentesListas fuentes) {
        this.importService = importService;
        this.productoRepository = productoRepository;
        this.bulkImporter = bulkImporter;
        this.batchRepo = batchRepo;
        this.historialRepo = historialRepo;
        this.syncRepo = syncRepo;
        this.revisionRepo = revisionRepo;
        this.fuentes = fuentes;
    }

    /**
     * @param forzar aplica aunque la lista haya llegado rara (despues de que una persona la miro)
     */
    void aplicar(SincronizacionPrecio s, FuenteLista f, List<FilaArchivo> filas, boolean forzar) {
        Tarifa tarifa = importService.obtenerTarifa(f.proveedor());
        Referencia ref = syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(f.proveedor(), BUENAS)
                .map(r -> new Referencia(r.getFilasLista()))
                .orElse(Referencia.NINGUNA);
        Set<String> skus = filas.stream().map(FilaArchivo::sku).filter(Objects::nonNull).map(String::trim)
                .filter(x -> !x.isEmpty()).collect(Collectors.toSet());
        Map<String, List<Producto>> productos = importService.buscarProductosPorSkuEnLotes(skus);

        Analisis a = AnalizadorListaPrecios.analizar(filas, productos, tarifa, f.proveedor(), f.umbrales(), ref,
                importService::parsearPrecio);

        s.setFilasLista(a.filasLista());
        s.setSinCambio(a.sinCambio());
        s.setNoEncontrados(a.noEncontrados());
        s.setFilasInvalidas(a.invalidas());

        List<String> problemas = new ArrayList<>(a.motivosParaFrenar());
        problemas.addAll(a.problemas());

        if (a.hayQueFrenar() && !forzar) {
            if (f.maxAltas() > 0 && !a.nuevos().isEmpty()) {
                problemas.add(n(a.nuevos().size()) + " código(s) de la lista son nuevos para " + f.proveedor()
                        + ejemplos(a.nuevos().stream().map(Nuevo::sku).toList())
                        + ": se dan de alta cuando la lista se aplique.");
            }
            s.setProblemas(String.join("\n", problemas));
            s.setResultado(ResultadoSincronizacion.RETENIDA);
            s.setMensaje("No se aplicó la lista de " + f.nombre() + " porque llegó con datos raros. Revisá los motivos y, "
                    + "si está todo bien, tocá «Aplicar igual».");
            terminar(s);
            log.warn("Lista de {} retenida: {}", f.nombre(), a.motivosParaFrenar());
            return;
        }

        List<Cambio> cambios = new ArrayList<>(a.cambios());
        int sinAlta = a.noEncontrados();
        if (f.maxAltas() > 0 && !a.nuevos().isEmpty()) {
            if (a.nuevos().size() <= f.maxAltas()) {
                sinAlta -= darDeAlta(f, a.nuevos(), filas, tarifa, cambios, problemas);
            } else {
                problemas.add(n(a.nuevos().size()) + " código(s) nuevos para " + f.proveedor()
                        + ejemplos(a.nuevos().stream().map(Nuevo::sku).toList()) + " son más de " + f.maxAltas()
                        + ": no se dio de alta ninguno. Revisalos y dalos de alta desde la importación manual.");
            }
        }
        s.setNoEncontrados(sinAlta);
        s.setProblemas(String.join("\n", problemas));

        LocalDateTime ahora = LocalDateTime.now();
        if (!cambios.isEmpty()) {
            ImportPrecioBatch batch = nuevoBatch(f.proveedor(), f.fuenteLote(s.getOrigen()), f.etiqueta(s.getOrigen()));
            List<HistorialPrecio> historial = new ArrayList<>(cambios.size());
            for (Cambio c : cambios) {
                historial.add(registrarCambio(c.producto(), c.costo(), c.venta(), tarifa, batch, ahora));
            }
            productoRepository.saveAll(cambios.stream().map(Cambio::producto).toList());
            historialRepo.saveAll(historial);
            cerrarBatch(batch, a.filasLista(), cambios.size(),
                    a.sinCambio() + sinAlta + a.saltos().size(), a.repetidos());
            s.setBatchId(batch.getId());
        }

        // Lo que habia quedado pendiente lo reemplaza lo que dice la lista de hoy.
        revisionRepo.vencerPendientes(EstadoRevisionPrecio.PENDIENTE, EstadoRevisionPrecio.VENCIDA, f.proveedor(), ahora);
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

        s.setActualizados(cambios.size());
        s.setEnRevision(a.saltos().size());
        s.setResultado(cambios.isEmpty() ? ResultadoSincronizacion.SIN_CAMBIOS : ResultadoSincronizacion.ACTUALIZADA);
        String mensaje = cambios.isEmpty()
                ? "La lista de " + f.nombre() + " no trae precios distintos a los que ya tenés."
                : "Se actualizaron " + n(cambios.size()) + " precio(s) de " + f.proveedor() + ".";
        if (!a.saltos().isEmpty()) mensaje += " " + n(a.saltos().size()) + " esperan tu revisión.";
        if (forzar && a.hayQueFrenar()) mensaje += " Se aplicó a pedido tuyo, aunque la lista llegó con datos raros.";
        s.setMensaje(mensaje);
        terminar(s);
        log.info("Lista de {} {}: {} ({} sin cambio, {} para revisar)", f.nombre(), s.getId(), s.getResultado(),
                a.sinCambio(), a.saltos().size());
    }

    /**
     * Crea los productos nuevos con el mismo importador masivo que usa la pantalla (marcas,
     * duplicados) y les pone precio en esta misma corrida, asi no quedan un dia sin precio.
     *
     * @return cuantos quedaron con producto y precio
     */
    private int darDeAlta(FuenteLista f, List<Nuevo> nuevos, List<FilaArchivo> filas, Tarifa tarifa,
                          List<Cambio> cambios, List<String> problemas) {
        Map<String, FilaArchivo> porSku = new HashMap<>();
        for (FilaArchivo fila : filas) {
            if (fila.sku() != null) porSku.putIfAbsent(fila.sku().trim(), fila);
        }
        List<ProductoImporter.FilaProducto> aCrear = new ArrayList<>(nuevos.size());
        for (Nuevo nuevo : nuevos) {
            FilaArchivo fila = porSku.get(nuevo.sku());
            boolean conDescripcion = fila != null && fila.descripcion() != null && !fila.descripcion().isBlank();
            boolean conMarca = fila != null && fila.marca() != null && !fila.marca().isBlank();
            aCrear.add(new ProductoImporter.FilaProducto(nuevo.sku(), conMarca ? fila.marca() : null, null,
                    conDescripcion ? fila.descripcion() : nuevo.sku(), null, null, f.proveedor()));
        }
        bulkImporter.importar(aCrear, new ImportJob(UUID.randomUUID().toString(), aCrear.size()));

        Map<String, List<Producto>> creados = importService.buscarProductosPorSkuEnLotes(
                nuevos.stream().map(Nuevo::sku).collect(Collectors.toSet()));
        List<String> conPrecio = new ArrayList<>();
        for (Nuevo nuevo : nuevos) {
            Producto p = AnalizadorListaPrecios.delProveedor(creados.getOrDefault(nuevo.sku(), List.of()), f.proveedor());
            if (p == null) continue;
            BigDecimal costo = tarifa.costoDesde(nuevo.precioLista());
            cambios.add(new Cambio(p, costo, tarifa.ventaDesde(costo)));
            conPrecio.add(nuevo.sku());
        }
        if (!conPrecio.isEmpty()) {
            problemas.add("Se dieron de alta " + n(conPrecio.size()) + " producto(s) nuevo(s) de " + f.proveedor()
                    + ejemplos(conPrecio) + ".");
        }
        if (conPrecio.size() < nuevos.size()) {
            problemas.add(n(nuevos.size() - conPrecio.size()) + " código(s) nuevo(s) no se pudieron dar de alta.");
        }
        return conPrecio.size();
    }

    // --- Revision de los precios que saltaron ---

    /** Aprueba los precios: se aplican con la configuracion de margenes de este momento. */
    RevisionPreciosResponse aplicarRevisiones(List<PrecioRevision> pendientes) {
        if (pendientes.isEmpty()) return new RevisionPreciosResponse(0, null, "No había precios pendientes para aplicar.");

        LocalDateTime ahora = LocalDateTime.now();
        Map<String, List<PrecioRevision>> porProveedor = pendientes.stream()
                .collect(Collectors.groupingBy(r -> r.getProducto().getProveedor(), LinkedHashMap::new, Collectors.toList()));
        Long primerLote = null;
        for (Map.Entry<String, List<PrecioRevision>> e : porProveedor.entrySet()) {
            FuenteLista f = fuentes.de(e.getKey());
            Tarifa tarifa = importService.obtenerTarifa(e.getKey());
            ImportPrecioBatch batch = nuevoBatch(e.getKey(), "API_SYNC", "Revisión de precios de " + f.nombre());
            List<HistorialPrecio> historial = new ArrayList<>();
            for (PrecioRevision r : e.getValue()) {
                BigDecimal costo = tarifa.costoDesde(r.getPrecioLista());
                historial.add(registrarCambio(r.getProducto(), costo, tarifa.ventaDesde(costo), tarifa, batch, ahora));
                r.setEstado(EstadoRevisionPrecio.APLICADA);
                r.setResueltaEn(ahora);
            }
            productoRepository.saveAll(e.getValue().stream().map(PrecioRevision::getProducto).toList());
            historialRepo.saveAll(historial);
            revisionRepo.saveAll(e.getValue());
            cerrarBatch(batch, e.getValue().size(), e.getValue().size(), 0, 0);
            if (primerLote == null) primerLote = batch.getId();
        }
        return new RevisionPreciosResponse(pendientes.size(), primerLote,
                "Se aplicaron " + n(pendientes.size()) + " precio(s). Si te equivocaste, podés revertirlo desde el historial.");
    }

    RevisionPreciosResponse descartarRevisiones(List<PrecioRevision> pendientes) {
        LocalDateTime ahora = LocalDateTime.now();
        pendientes.forEach(r -> {
            r.setEstado(EstadoRevisionPrecio.DESCARTADA);
            r.setResueltaEn(ahora);
        });
        revisionRepo.saveAll(pendientes);
        return new RevisionPreciosResponse(pendientes.size(), null, pendientes.isEmpty()
                ? "No había precios pendientes para descartar."
                : "Se descartaron " + n(pendientes.size()) + " precio(s): quedan como estaban. "
                  + "Si el proveedor los sigue publicando así, vuelven a aparecer en la próxima actualización.");
    }

    // --- Piezas comunes ---

    private ImportPrecioBatch nuevoBatch(String proveedor, String fuente, String archivo) {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setProveedor(proveedor);
        batch.setFuente(fuente);
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

    void terminarConError(SincronizacionPrecio s, String mensaje) {
        s.setResultado(ResultadoSincronizacion.ERROR);
        s.setMensaje(mensaje);
        terminar(s);
        log.warn("Actualizacion de precios {} fallo: {}", s.getId(), mensaje);
    }

    void terminar(SincronizacionPrecio s) {
        s.setTerminadaEn(LocalDateTime.now());
        syncRepo.save(s);
    }

    SincronizacionPrecio nueva(FuenteLista f, OrigenSincronizacion origen, boolean forzar) {
        SincronizacionPrecio s = new SincronizacionPrecio();
        s.setProveedor(f.proveedor());
        s.setOrigen(origen);
        s.setForzada(forzar);
        return syncRepo.save(s);
    }
}
