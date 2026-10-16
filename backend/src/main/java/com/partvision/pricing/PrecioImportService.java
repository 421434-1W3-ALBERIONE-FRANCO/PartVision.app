package com.partvision.pricing;

import com.partvision.catalog.domain.CodigoProducto;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.HistorialPrecio;
import com.partvision.pricing.domain.ImportPrecioBatch;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.imports.service.ProductoImporter;
import com.partvision.pricing.dto.PrecioAltaFaltantesResponse;
import com.partvision.pricing.dto.PrecioBatchResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse.PreviewFila;
import com.partvision.pricing.dto.PrecioImportProgresoResponse;
import com.partvision.pricing.dto.PrecioImportResultResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecioImportService {

    private final ProductoRepository productoRepository;
    private final ConfiguracionPrecioRepository configuracionRepo;
    private final ImportPrecioBatchRepository batchRepo;
    private final HistorialPrecioRepository historialRepo;
    private final ProductoBulkImporter bulkImporter;

    private record UploadInfo(byte[] contenido, boolean esExcel, Instant subidoEn) {}

    /**
     * Archivos subidos esperando preview/aplicar. Guardan el contenido completo en memoria
     * (una lista de precios ronda los megabytes), y el flujo permite abandonar la
     * importacion a mitad de camino, asi que se vencen solos: sin esto el heap crece hasta
     * tirar el proceso.
     */
    private final Map<String, UploadInfo> uploads = new ConcurrentHashMap<>();

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(30);
    private static final int MAX_UPLOADS = 4;

    private final AtomicBoolean importando = new AtomicBoolean(false);
    private final AtomicInteger progresoActual = new AtomicInteger(0);
    private final AtomicInteger progresoTotal = new AtomicInteger(0);
    private final AtomicReference<PrecioImportResultResponse> ultimoResultadoImport = new AtomicReference<>();

    public boolean iniciarImport() {
        return importando.compareAndSet(false, true);
    }

    /**
     * Libera el candado que tomo {@link #iniciarImport()}. Lo usa la actualizacion automatica
     * de precios, que comparte el candado para no pisarse con una importacion manual.
     */
    void terminarImport() {
        importando.set(false);
    }

    public PrecioImportProgresoResponse getProgresoImport() {
        return new PrecioImportProgresoResponse(
                importando.get(), progresoActual.get(), progresoTotal.get(),
                ultimoResultadoImport.get());
    }

    public PrecioImportColumnasResponse detectarColumnas(byte[] contenido, String nombreArchivo) {
        String uploadId = UUID.randomUUID().toString();
        boolean esExcel = esArchivoExcel(nombreArchivo);
        purgarUploads();
        uploads.put(uploadId, new UploadInfo(contenido, esExcel, Instant.now()));

        try {
            List<String> columnas;
            int totalFilas;

            if (esExcel) {
                try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
                    Sheet sheet = wb.getSheetAt(0);
                    int headerIdx = detectarFilaHeader(sheet);
                    Row headerRow = sheet.getRow(headerIdx);
                    if (headerRow == null) throw new IllegalArgumentException("El archivo está vacío");
                    columnas = new ArrayList<>();
                    for (Cell cell : headerRow) {
                        String val = cellToString(cell);
                        if (val != null && !val.isBlank()) columnas.add(val);
                    }
                    if (columnas.isEmpty()) throw new IllegalArgumentException("No se encontraron columnas con datos en la primera fila");
                    totalFilas = sheet.getLastRowNum() - headerIdx;
                    log.info("Excel detectado: ~{} filas de datos, {} columnas (header en fila {})",
                            totalFilas, columnas.size(), headerIdx + 1);
                }
            } else {
                try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8);
                     CSVParser parser = CSVParser.parse(reader, csvFormat())) {
                    columnas = parser.getHeaderNames();
                    totalFilas = (int) parser.stream().count();
                }
            }

            return new PrecioImportColumnasResponse(uploadId, columnas, totalFilas);
        } catch (IllegalArgumentException e) {
            uploads.remove(uploadId);
            throw e;
        } catch (Exception e) {
            uploads.remove(uploadId);
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    public PrecioImportPreviewResponse preview(String uploadId, String colSku, String colPrecio, String proveedor) {
        UploadInfo info = uploads.get(uploadId);
        if (info == null) {
            throw new IllegalArgumentException("Archivo no encontrado. Volvé a subirlo.");
        }

        Tarifa tarifa = obtenerTarifa(proveedor);

        List<FilaArchivo> filas = parsearFilas(info, colSku, colPrecio);
        ProductosDeLaLista productos = buscarProductos(filas, proveedor);
        Map<String, Integer> primeraFila = new HashMap<>();
        for (int i = 0; i < filas.size(); i++) {
            if (filas.get(i).sku() != null) primeraFila.putIfAbsent(filas.get(i).sku().trim(), i + 2);
        }

        // Solo se devuelve una muestra de filas: el navegador no puede renderizar
        // decenas de miles de <tr> sin congelarse. Los contadores si son del total.
        // Un codigo repetido en el archivo cuenta una vez: es un solo producto.
        List<PreviewFila> muestra = new ArrayList<>();
        List<PrecioImportPreviewResponse.FilaNoEncontrada> detalleNoEncontrados = new ArrayList<>();
        int total = 0, ok = 0, conflictos = 0, noEncontrados = 0;

        for (Map.Entry<String, List<FilaArchivo>> e : filasPorCodigo(filas).entrySet()) {
            String sku = e.getKey();
            FilaArchivo primera = e.getValue().getFirst();
            int fila = primeraFila.get(sku);
            Resolucion r = resolver(e.getValue(), productos.porCodigo().getOrDefault(sku, List.of()), proveedor);
            BigDecimal precioArchivo = r.precio() != null ? r.precio() : parsearPrecio(primera.precio());
            BigDecimal costo = tarifa.costoDesde(precioArchivo);
            BigDecimal precioNuevo = tarifa.ventaDesde(costo);
            total++;

            PreviewFila pf;
            if (Resolucion.NO_ENCONTRADO.equals(r.estado())) {
                pf = new PreviewFila(fila, sku, costo, Resolucion.NO_ENCONTRADO,
                        null, null, null, null, precioNuevo, 0);
                noEncontrados++;
                if (detalleNoEncontrados.size() < MAX_NO_ENCONTRADOS) {
                    detalleNoEncontrados.add(new PrecioImportPreviewResponse.FilaNoEncontrada(
                            sku, primera.descripcion(), costo));
                }
            } else if (Resolucion.CONFLICTO.equals(r.estado())) {
                String descs = r.candidatos().size() == 1
                        ? "El código viene repetido con precios distintos y no se sabe cuál es de "
                          + r.candidatos().getFirst().getDescripcion()
                        : r.candidatos().stream()
                                .map(p -> p.getDescripcion() + (p.getMarca() != null ? " [" + p.getMarca().getNombre() + "]" : ""))
                                .collect(Collectors.joining(" | "));
                pf = new PreviewFila(fila, sku, costo, Resolucion.CONFLICTO,
                        null, descs, null, null, precioNuevo, r.candidatos().size());
                conflictos++;
            } else {
                Producto p = r.producto();
                String marca = p.getMarca() != null ? p.getMarca().getNombre() : null;
                pf = new PreviewFila(fila, sku, costo, Resolucion.OK,
                        p.getId(), p.getDescripcion(), marca, p.getPrecioCosto(), precioNuevo, 1);
                ok++;
            }
            if (muestra.size() < PREVIEW_MAX_FILAS) muestra.add(pf);
        }

        return new PrecioImportPreviewResponse(muestra, total, ok, conflictos, noEncontrados,
                tarifa.margen(), detalleNoEncontrados);
    }

    /**
     * Da de alta en el catalogo los SKU del archivo que no existen todavia. Se delega en el
     * importador masivo para no duplicar la resolucion de marcas ni los chequeos de duplicados.
     * Los productos quedan sin precio: se los pone la importacion de precios, que ahora si
     * los va a encontrar.
     *
     * @param skus cuales dar de alta; vacio o null significa todos los no encontrados.
     */
    public PrecioAltaFaltantesResponse crearFaltantes(String uploadId, String colSku, String colPrecio,
                                                       String proveedor, Set<String> skus) {
        UploadInfo info = uploads.get(uploadId);
        if (info == null) {
            throw new IllegalArgumentException("Archivo no encontrado. Volvé a subirlo.");
        }

        List<FilaArchivo> filas = parsearFilas(info, colSku, colPrecio);
        // Mismo criterio que la vista previa: falta si no hay producto de este proveedor (ni con
        // ese codigo ni con el mismo escrito de otra forma). Que exista en otro proveedor no
        // impide darlo de alta: es el mismo repuesto con otro costo, como los ~10.300 que ya estan.
        Map<String, List<Producto>> existentes = buscarProductos(filas, proveedor).porCodigo();

        boolean todos = skus == null || skus.isEmpty();
        Set<String> yaVistos = new HashSet<>();
        List<ProductoImporter.FilaProducto> aCrear = new ArrayList<>();

        for (FilaArchivo fila : filas) {
            String sku = fila.sku();
            if (sku == null || sku.isBlank()) continue;
            if (!todos && !skus.contains(sku)) continue;
            if (!elegibles(existentes.getOrDefault(sku, List.of()), proveedor).isEmpty()) continue;
            if (!yaVistos.add(sku)) continue;

            String descripcion = fila.descripcion() != null && !fila.descripcion().isBlank()
                    ? fila.descripcion()
                    : sku;
            aCrear.add(new ProductoImporter.FilaProducto(
                    sku, fila.marca(), null, descripcion, null, null, proveedor));
        }

        if (aCrear.isEmpty()) {
            return new PrecioAltaFaltantesResponse(0, 0, "No había productos nuevos para dar de alta.");
        }

        ImportJob job = new ImportJob(UUID.randomUUID().toString(), aCrear.size());
        bulkImporter.importar(aCrear, job);

        String mensaje = String.format("%d producto(s) dado(s) de alta, %d omitido(s).",
                job.getImportados(), job.getOmitidos());
        log.info("Alta de faltantes ({}): {}", proveedor, mensaje);
        return new PrecioAltaFaltantesResponse(job.getImportados(), job.getOmitidos(), mensaje);
    }

    public void validarAplicar(String uploadId, String proveedor) {
        if (!uploads.containsKey(uploadId)) {
            throw new IllegalArgumentException("Archivo expirado. Volvé a subirlo.");
        }
        obtenerTarifa(proveedor);
    }

    @Async("importExecutor")
    @Transactional
    public void ejecutarImportAsync(String uploadId, String colSku, String colPrecio,
                                     String proveedor, Set<String> skusExcluidos, String archivo) {
        progresoActual.set(0);
        progresoTotal.set(0);
        ultimoResultadoImport.set(null);
        try {
            PrecioImportResultResponse result = aplicar(uploadId, colSku, colPrecio, proveedor, skusExcluidos, archivo);
            ultimoResultadoImport.set(result);
        } catch (Exception e) {
            log.error("Error en importación async", e);
            ultimoResultadoImport.set(new PrecioImportResultResponse(0, 0, 0, 0, 0,
                    "Error durante la importación: " + e.getMessage()));
        } finally {
            importando.set(false);
        }
    }

    private PrecioImportResultResponse aplicar(String uploadId, String colSku, String colPrecio,
                                               String proveedor, Set<String> skusExcluidos, String archivo) {
        UploadInfo info = uploads.remove(uploadId);
        if (info == null) {
            throw new IllegalArgumentException("Archivo expirado. Volvé a subirlo.");
        }

        Tarifa tarifa = obtenerTarifa(proveedor);

        List<FilaArchivo> filas = parsearFilas(info, colSku, colPrecio);
        Map<String, List<FilaArchivo>> porCodigo = filasPorCodigo(filas);
        progresoTotal.set(porCodigo.size());

        ProductosDeLaLista productos = buscarProductos(filas, proveedor);

        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setProveedor(proveedor);
        batch.setFuente("CSV_IMPORT");
        batch.setArchivo(archivo);
        batchRepo.save(batch);

        int aplicados = 0, omitidos = 0, conflictos = 0, sinCambio = 0;
        List<Producto> modificados = new ArrayList<>();
        List<HistorialPrecio> historiales = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        // Un codigo repetido en el archivo es un solo producto y se escribe una sola vez.
        for (Map.Entry<String, List<FilaArchivo>> e : porCodigo.entrySet()) {
            String sku = e.getKey();
            progresoActual.incrementAndGet();

            if (skusExcluidos != null && skusExcluidos.contains(sku)) {
                omitidos++;
                continue;
            }

            Resolucion r = resolver(e.getValue(), productos.porCodigo().getOrDefault(sku, List.of()), proveedor);
            if (!Resolucion.OK.equals(r.estado())) {
                if (Resolucion.CONFLICTO.equals(r.estado())) conflictos++;
                else omitidos++;
                continue;
            }

            Producto p = r.producto();
            BigDecimal costo = tarifa.costoDesde(r.precio());
            BigDecimal precioVenta = tarifa.ventaDesde(costo);

            // Un precio que no cambio no se escribe: ni el producto ni una fila de historial.
            // Reescribir 67 mil productos iguales todos los dias llenaba el historial (88% de sus
            // filas eran asi) y obligaba a tener en memoria dos entidades por producto: la carga
            // diaria de EGSA dejo el backend colgado varias veces.
            if (igual(p.getPrecioCosto(), costo) && igual(p.getPrecioVenta(), precioVenta)) {
                sinCambio++;
                continue;
            }

            HistorialPrecio h = new HistorialPrecio();
            h.setProducto(p);
            h.setBatch(batch);
            h.setPrecioCostoAnterior(p.getPrecioCosto());
            h.setPrecioVentaAnterior(p.getPrecioVenta());
            h.setPrecioCostoNuevo(costo);
            h.setPrecioVentaNuevo(precioVenta);
            h.setMargenAplicado(tarifa.margen());
            historiales.add(h);

            p.setPrecioCosto(costo);
            p.setPrecioVenta(precioVenta);
            p.setPrecioActualizadoEn(ahora);
            modificados.add(p);
            aplicados++;
        }

        if (!modificados.isEmpty()) productoRepository.saveAll(modificados);
        if (!historiales.isEmpty()) historialRepo.saveAll(historiales);

        batch.setTotal(aplicados + sinCambio + omitidos + conflictos);
        batch.setAplicados(aplicados);
        batch.setOmitidos(omitidos);
        batch.setConflictos(conflictos);
        batchRepo.save(batch);

        String mensaje = String.format(
                "Importación completada: %d aplicados, %d sin cambio, %d omitidos, %d conflictos (ajuste %.2f%%, margen %.2f%%)",
                aplicados, sinCambio, omitidos, conflictos, tarifa.ajusteLista(), tarifa.margen());
        log.info(mensaje);

        return new PrecioImportResultResponse(batch.getId(), batch.getTotal(), aplicados, omitidos, conflictos, mensaje);
    }

    @Transactional
    public PrecioBatchResponse rollback(Long batchId) {
        ImportPrecioBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch no encontrado"));

        if ("REVERTIDO".equals(batch.getEstado())) {
            throw new IllegalStateException("Este batch ya fue revertido");
        }

        List<HistorialPrecio> historiales = historialRepo.findByBatchId(batchId);
        List<Producto> restaurados = new ArrayList<>();
        int revertidos = 0;

        for (HistorialPrecio h : historiales) {
            Producto p = h.getProducto();
            boolean costoCoincide = Objects.equals(p.getPrecioCosto(), h.getPrecioCostoNuevo());
            boolean ventaCoincide = Objects.equals(p.getPrecioVenta(), h.getPrecioVentaNuevo());

            if (costoCoincide && ventaCoincide) {
                p.setPrecioCosto(h.getPrecioCostoAnterior());
                p.setPrecioVenta(h.getPrecioVentaAnterior());
                p.setPrecioActualizadoEn(LocalDateTime.now());
                restaurados.add(p);
                revertidos++;
            } else {
                log.warn("SKU {} tiene precio modificado después del batch {}, no se revierte",
                        p.getSku(), batchId);
            }
        }

        if (!restaurados.isEmpty()) productoRepository.saveAll(restaurados);

        batch.setEstado("REVERTIDO");
        batchRepo.save(batch);

        log.info("Rollback batch {}: {} de {} productos revertidos", batchId, revertidos, historiales.size());
        return PrecioBatchResponse.from(batch);
    }

    public List<PrecioBatchResponse> listarBatches() {
        return batchRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(PrecioBatchResponse::from)
                .toList();
    }

    /** Descarta los archivos vencidos y, si aun quedan de mas, los mas viejos. */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void purgarUploads() {
        Instant limite = Instant.now().minus(UPLOAD_TTL);
        uploads.entrySet().removeIf(e -> e.getValue().subidoEn().isBefore(limite));

        if (uploads.size() > MAX_UPLOADS) {
            uploads.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getValue().subidoEn()))
                    .limit(uploads.size() - MAX_UPLOADS)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(uploads::remove);
        }
    }

    // --- Helpers ---

    private static boolean igual(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static final int SKU_BATCH_SIZE = 10_000;
    private static final int PREVIEW_MAX_FILAS = 500;
    private static final int MAX_NO_ENCONTRADOS = 5_000;

    /**
     * Los productos que puede tocar la lista de un proveedor: los suyos y, si no tiene ninguno
     * con ese codigo, los que no tienen proveedor. Nunca los de otro: un mismo codigo existe una
     * vez por proveedor (~10.300 codigos estan en los dos) y cada uno tiene su costo. Hasta el
     * 2026-10-15 un candidato unico se tomaba aunque fuera de otro proveedor: la lista de ADS
     * del 13/10 le puso su precio a 29 productos de EGSA. Sin proveedor no hay como elegir.
     */
    List<Producto> elegibles(List<Producto> candidatos, String proveedor) {
        if (proveedor == null || proveedor.isBlank()) return candidatos;
        List<Producto> delProveedor = candidatos.stream().filter(p -> esDelProveedor(p, proveedor)).toList();
        if (!delProveedor.isEmpty()) return delProveedor;
        return candidatos.stream().filter(p -> p.getProveedor() == null || p.getProveedor().isBlank()).toList();
    }

    private static boolean esDelProveedor(Producto p, String proveedor) {
        return p.getProveedor() != null && p.getProveedor().trim().equalsIgnoreCase(proveedor.trim());
    }

    /**
     * Lo que la lista trae y el catalogo tiene: los productos de cada codigo, y cuales se
     * reconocieron aunque el proveedor los mandara escritos de otra forma.
     *
     * @param conOtroFormato codigo de la lista, codigo del catalogo
     */
    record ProductosDeLaLista(Map<String, List<Producto>> porCodigo, Map<String, String> conOtroFormato) {}

    /**
     * Los productos de cada codigo de la lista: los de ese codigo exacto (de cualquier
     * proveedor; elige quien llama) y, para los codigos sin producto de este proveedor, el que
     * tiene la misma {@link CodigoProducto#clave clave}. El 2026-10-15 EGSA paso a mandar 651
     * codigos de PACKSON con un guion de mas ({@code PK-2000} por {@code PK2000}): sin esto, esos
     * productos se quedaban sin precio y aparecian como nuevos, y darlos de alta los duplicaba.
     *
     * <p>La clave sola no alcanza: hay productos distintos que solo se diferencian en un guion
     * ({@code PK3000} es SABI y {@code PK-3000} es PACKSON). Se empareja solo si la lista dice la
     * marca y hay un unico producto del proveedor con esa clave y esa marca, que la lista no
     * traiga ya con su propio codigo, y un solo codigo de la lista apunta a el. Ante la duda, no:
     * mejor un codigo "no encontrado" que el precio de otro producto.
     */
    ProductosDeLaLista buscarProductos(List<FilaArchivo> filas, String proveedor) {
        Set<String> skus = filas.stream().map(FilaArchivo::sku).filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<Producto>> porCodigo = new HashMap<>(buscarProductosPorSkuEnLotes(skus));
        if (proveedor == null || proveedor.isBlank()) return new ProductosDeLaLista(porCodigo, Map.of());

        Map<String, Set<String>> marcasPorCodigo = new HashMap<>();
        for (FilaArchivo f : filas) {
            if (f.sku() == null || f.sku().isBlank()) continue;
            marcasPorCodigo.computeIfAbsent(f.sku().trim(), k -> new HashSet<>())
                    .add(Objects.toString(AnalizadorListaPrecios.normalizarMarca(f.marca()), ""));
        }
        Set<Long> tomados = new HashSet<>();
        Map<String, List<String>> faltantesPorClave = new HashMap<>();
        for (String sku : skus) {
            Producto propio = AnalizadorListaPrecios.delProveedor(porCodigo.getOrDefault(sku, List.of()), proveedor);
            if (propio != null) {
                tomados.add(propio.getId());
                continue;
            }
            Set<String> marcas = marcasPorCodigo.getOrDefault(sku, Set.of());
            if (marcas.size() != 1 || marcas.contains("")) continue;   // sin marca o con varias: no hay como confirmar
            faltantesPorClave.computeIfAbsent(CodigoProducto.clave(sku), k -> new ArrayList<>()).add(sku);
        }
        if (faltantesPorClave.isEmpty()) return new ProductosDeLaLista(porCodigo, Map.of());

        Map<String, List<Producto>> candidatosPorClave = buscarPorClaveEnLotes(faltantesPorClave.keySet()).stream()
                .filter(p -> esDelProveedor(p, proveedor))
                .collect(Collectors.groupingBy(p -> CodigoProducto.clave(p.getSku())));
        Map<String, String> conOtroFormato = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : faltantesPorClave.entrySet()) {
            if (e.getValue().size() != 1) continue;                     // dos codigos de la lista con la misma clave
            String sku = e.getValue().getFirst();
            String marca = marcasPorCodigo.get(sku).iterator().next();
            List<Producto> deEsaMarca = candidatosPorClave.getOrDefault(e.getKey(), List.of()).stream()
                    .filter(p -> p.getMarca() != null
                            && marca.equals(AnalizadorListaPrecios.normalizarMarca(p.getMarca().getNombre())))
                    .toList();
            if (deEsaMarca.size() != 1 || tomados.contains(deEsaMarca.getFirst().getId())) continue;
            Producto p = deEsaMarca.getFirst();
            tomados.add(p.getId());
            porCodigo.computeIfAbsent(sku, k -> new ArrayList<>()).add(p);
            conOtroFormato.put(sku, p.getSku());
        }
        return new ProductosDeLaLista(porCodigo, conOtroFormato);
    }

    private List<Producto> buscarPorClaveEnLotes(Set<String> claves) {
        List<String> lista = new ArrayList<>(claves);
        List<Producto> resultado = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += SKU_BATCH_SIZE) {
            resultado.addAll(productoRepository.findByClaveSkuIn(lista.subList(i, Math.min(i + SKU_BATCH_SIZE, lista.size()))));
        }
        return resultado;
    }

    /**
     * Que le toca a un codigo de la lista. {@code precio} es el de la lista (antes de tarifa).
     * CONFLICTO con un solo candidato quiere decir que el codigo viene repetido con precios
     * distintos y no se sabe cual es el de este producto.
     */
    record Resolucion(String estado, Producto producto, BigDecimal precio, List<Producto> candidatos) {
        static final String OK = "OK";
        static final String NO_ENCONTRADO = "NO_ENCONTRADO";
        static final String CONFLICTO = "CONFLICTO";
    }

    /**
     * Las mismas reglas que las actualizaciones automaticas ({@link AnalizadorListaPrecios}):
     * solo productos del proveedor, y si el codigo viene repetido con precios distintos (EGSA
     * usa el 2144 para un arbol de levas BH y un tornillo de REY GOMA), el precio de la fila de
     * su marca. Antes ganaba la ultima fila del archivo: asi el arbol de levas quedo a $579.
     */
    Resolucion resolver(List<FilaArchivo> filasDelCodigo, List<Producto> candidatos, String proveedor) {
        List<Producto> posibles = elegibles(candidatos, proveedor);
        if (posibles.isEmpty()) return new Resolucion(Resolucion.NO_ENCONTRADO, null, null, posibles);
        if (posibles.size() > 1) return new Resolucion(Resolucion.CONFLICTO, null, null, posibles);
        Producto p = posibles.getFirst();
        List<AnalizadorListaPrecios.Valida> validas = filasDelCodigo.stream()
                .map(f -> new AnalizadorListaPrecios.Valida(parsearPrecio(f.precio()), f.marca()))
                .toList();
        BigDecimal precio = AnalizadorListaPrecios.mismoPrecio(validas)
                ? validas.getFirst().precio()
                : AnalizadorListaPrecios.precioDeSuMarca(p, validas);
        if (precio == null) return new Resolucion(Resolucion.CONFLICTO, null, null, posibles);
        return new Resolucion(Resolucion.OK, p, precio, posibles);
    }

    /**
     * Las filas con codigo y precio valido, agrupadas por codigo en el orden del archivo. Un
     * precio en cero, negativo o disparatado no se aplica, igual que en las actualizaciones
     * automaticas: la lista de EGSA trae ~2.700 filas en $0 (IMPORTADO, SINTERMETAL, PACKSON) y
     * la importacion vieja las cargaba, dejando esos productos en cero.
     */
    private Map<String, List<FilaArchivo>> filasPorCodigo(List<FilaArchivo> filas) {
        Map<String, List<FilaArchivo>> porCodigo = new LinkedHashMap<>();
        for (FilaArchivo f : filas) {
            if (f.sku() == null || f.sku().isBlank()) continue;
            BigDecimal precio = parsearPrecio(f.precio());
            if (precio == null || precio.signum() <= 0 || precio.compareTo(AnalizadorListaPrecios.PRECIO_MAXIMO) >= 0) continue;
            porCodigo.computeIfAbsent(f.sku().trim(), k -> new ArrayList<>()).add(f);
        }
        return porCodigo;
    }

    Map<String, List<Producto>> buscarProductosPorSkuEnLotes(Set<String> skus) {
        if (skus.size() <= SKU_BATCH_SIZE) {
            return productoRepository.findBySkuIn(skus)
                    .stream().collect(Collectors.groupingBy(Producto::getSku));
        }
        Map<String, List<Producto>> resultado = new HashMap<>();
        List<String> lista = new ArrayList<>(skus);
        for (int i = 0; i < lista.size(); i += SKU_BATCH_SIZE) {
            List<String> lote = lista.subList(i, Math.min(i + SKU_BATCH_SIZE, lista.size()));
            productoRepository.findBySkuIn(lote)
                    .forEach(p -> resultado.computeIfAbsent(p.getSku(), k -> new ArrayList<>()).add(p));
        }
        return resultado;
    }

    /**
     * El archivo del proveedor no siempre trae el precio que se paga: algunos exportan
     * su precio de lista y aplican un recargo aparte. 'ajusteLista' salva esa diferencia.
     */
    record Tarifa(BigDecimal margen, BigDecimal ajusteLista) {
        BigDecimal costoDesde(BigDecimal precioArchivo) {
            return precioArchivo
                    .multiply(BigDecimal.ONE.add(ajusteLista.divide(BigDecimal.valueOf(100))))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ventaDesde(BigDecimal costo) {
            return costo
                    .multiply(BigDecimal.ONE.add(margen.divide(BigDecimal.valueOf(100))))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    Tarifa obtenerTarifa(String proveedor) {
        return configuracionRepo.findByProveedorIgnoreCase(proveedor)
                .map(c -> new Tarifa(c.getMargen(),
                        c.getAjusteLista() != null ? c.getAjusteLista() : BigDecimal.ZERO))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay configuración de margen para el proveedor: " + proveedor));
    }

    /**
     * Descripcion y marca se toman de las columnas que el proveedor traiga con esos nombres;
     * no todos los archivos las incluyen. El matcheo de precios nunca las mira: sirven para
     * poder mostrar y dar de alta los SKU que no existen en el catalogo.
     */
    record FilaArchivo(String sku, String precio, String descripcion, String marca) {}

    private List<FilaArchivo> parsearFilas(UploadInfo info, String colSku, String colPrecio) {
        if (info.esExcel()) {
            return parsearFilasExcel(info.contenido(), colSku, colPrecio);
        }
        return parsearFilasCsv(info.contenido(), colSku, colPrecio);
    }

    private boolean esColumnaDescripcion(String nombre) {
        if (nombre == null) return false;
        String n = nombre.trim().toLowerCase();
        return n.startsWith("descripc") || n.startsWith("descript") || n.equals("detalle");
    }

    private boolean esColumnaMarca(String nombre) {
        return nombre != null && nombre.trim().equalsIgnoreCase("marca");
    }

    private List<FilaArchivo> parsearFilasCsv(byte[] contenido, String colSku, String colPrecio) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, csvFormat())) {

            List<String> headers = parser.getHeaderNames();
            String colDesc = headers.stream().filter(this::esColumnaDescripcion).findFirst().orElse(null);
            String colMarca = headers.stream().filter(this::esColumnaMarca).findFirst().orElse(null);

            List<FilaArchivo> filas = new ArrayList<>();
            for (CSVRecord record : parser) {
                filas.add(new FilaArchivo(
                        valor(record, colSku),
                        valor(record, colPrecio),
                        colDesc != null ? valor(record, colDesc) : null,
                        colMarca != null ? valor(record, colMarca) : null));
            }
            return filas;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al leer el CSV: " + e.getMessage());
        }
    }

    List<FilaArchivo> parsearFilasExcel(byte[] contenido, String colSku, String colPrecio) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            Sheet sheet = wb.getSheetAt(0);
            int headerIdx = detectarFilaHeader(sheet);
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) throw new IllegalArgumentException("El archivo está vacío");

            int colSkuIdx = -1, colPrecioIdx = -1, colDescIdx = -1, colMarcaIdx = -1;
            for (Cell cell : headerRow) {
                String nombre = cellToString(cell);
                if (nombre == null) continue;
                if (nombre.equalsIgnoreCase(colSku)) colSkuIdx = cell.getColumnIndex();
                if (nombre.equalsIgnoreCase(colPrecio)) colPrecioIdx = cell.getColumnIndex();
                if (colDescIdx < 0 && esColumnaDescripcion(nombre)) colDescIdx = cell.getColumnIndex();
                if (colMarcaIdx < 0 && esColumnaMarca(nombre)) colMarcaIdx = cell.getColumnIndex();
            }
            if (colSkuIdx < 0) throw new IllegalArgumentException("Columna SKU '" + colSku + "' no encontrada");
            if (colPrecioIdx < 0) throw new IllegalArgumentException("Columna precio '" + colPrecio + "' no encontrada");

            List<FilaArchivo> filas = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() <= headerIdx) continue;
                String sku = cellToString(row.getCell(colSkuIdx));
                if (sku == null || sku.isBlank()) continue;
                filas.add(new FilaArchivo(
                        sku.trim(),
                        textoCelda(row, colPrecioIdx),
                        textoCelda(row, colDescIdx),
                        textoCelda(row, colMarcaIdx)));
            }
            log.info("Excel parseado: {} filas con datos (columnas: sku={} idx={}, precio={} idx={})",
                    filas.size(), colSku, colSkuIdx, colPrecio, colPrecioIdx);
            return filas;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al leer el archivo Excel: " + e.getMessage());
        }
    }

    /**
     * Algunos proveedores ponen el nombre de la empresa como titulo en la primera fila
     * y los encabezados reales debajo. Se toma como header la primera fila con 2 o mas
     * celdas con texto.
     */
    private int detectarFilaHeader(Sheet sheet) {
        int limite = Math.min(sheet.getLastRowNum(), 10);
        for (int i = 0; i <= limite; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            int conTexto = 0;
            for (Cell cell : row) {
                String val = cellToString(cell);
                if (val != null && !val.isBlank()) conTexto++;
            }
            if (conTexto >= 2) return i;
        }
        return 0;
    }

    private String textoCelda(Row row, int idx) {
        if (idx < 0) return null;
        String v = cellToString(row.getCell(idx));
        return v != null ? v.trim() : null;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> null;
        };
    }

    private boolean esArchivoExcel(String nombre) {
        if (nombre == null) return false;
        String lower = nombre.toLowerCase();
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    private String valor(CSVRecord record, String columna) {
        try {
            String v = record.get(columna);
            return v == null || v.isBlank() ? null : v.trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    BigDecimal parsearPrecio(String valor) {
        if (valor == null) return null;
        try {
            String limpio = valor.replace("$", "").replace(",", ".").replaceAll("\\s", "").trim();
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private CSVFormat csvFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();
    }
}
