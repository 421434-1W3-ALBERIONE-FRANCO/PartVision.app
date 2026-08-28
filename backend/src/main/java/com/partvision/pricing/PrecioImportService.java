package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.HistorialPrecio;
import com.partvision.pricing.domain.ImportPrecioBatch;
import com.partvision.pricing.dto.PrecioBatchResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse.PreviewFila;
import com.partvision.pricing.dto.PrecioImportResultResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecioImportService {

    private final ProductoRepository productoRepository;
    private final ConfiguracionPrecioRepository configuracionRepo;
    private final ImportPrecioBatchRepository batchRepo;
    private final HistorialPrecioRepository historialRepo;

    private final Map<String, byte[]> uploads = new ConcurrentHashMap<>();

    public PrecioImportColumnasResponse detectarColumnas(byte[] contenido) {
        String uploadId = UUID.randomUUID().toString();
        uploads.put(uploadId, contenido);

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, csvFormat())) {

            List<String> columnas = parser.getHeaderNames();
            int totalFilas = (int) parser.stream().count();
            return new PrecioImportColumnasResponse(uploadId, columnas, totalFilas);
        } catch (Exception e) {
            uploads.remove(uploadId);
            throw new IllegalArgumentException("No se pudo leer el archivo CSV: " + e.getMessage());
        }
    }

    public PrecioImportPreviewResponse preview(String uploadId, String colSku, String colPrecio, String proveedor) {
        byte[] contenido = uploads.get(uploadId);
        if (contenido == null) {
            throw new IllegalArgumentException("Archivo no encontrado. Volvé a subirlo.");
        }

        BigDecimal margen = obtenerMargen(proveedor);
        BigDecimal multiplicador = BigDecimal.ONE.add(margen.divide(BigDecimal.valueOf(100)));

        List<String[]> filasCsv = parsearFilas(contenido, colSku, colPrecio);

        Set<String> skusUnicos = filasCsv.stream()
                .map(f -> f[0])
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        Map<String, List<Producto>> productosPorSku = productoRepository.findBySkuIn(skusUnicos)
                .stream()
                .collect(Collectors.groupingBy(Producto::getSku));

        List<PreviewFila> preview = new ArrayList<>();
        int ok = 0, conflictos = 0, noEncontrados = 0;

        for (int i = 0; i < filasCsv.size(); i++) {
            String[] fila = filasCsv.get(i);
            String sku = fila[0];
            BigDecimal precioCsv = parsearPrecio(fila[1]);

            if (sku == null || sku.isBlank()) continue;
            if (precioCsv == null) continue;

            List<Producto> matches = productosPorSku.getOrDefault(sku, List.of());
            BigDecimal precioNuevo = precioCsv.multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);

            if (matches.isEmpty()) {
                preview.add(new PreviewFila(i + 2, sku, precioCsv, "NO_ENCONTRADO",
                        null, null, null, null, precioNuevo, 0));
                noEncontrados++;
            } else if (matches.size() > 1) {
                String descs = matches.stream()
                        .map(p -> p.getDescripcion() + (p.getMarca() != null ? " [" + p.getMarca().getNombre() + "]" : ""))
                        .collect(Collectors.joining(" | "));
                preview.add(new PreviewFila(i + 2, sku, precioCsv, "CONFLICTO",
                        null, descs, null, null, precioNuevo, matches.size()));
                conflictos++;
            } else {
                Producto p = matches.getFirst();
                String marca = p.getMarca() != null ? p.getMarca().getNombre() : null;
                preview.add(new PreviewFila(i + 2, sku, precioCsv, "OK",
                        p.getId(), p.getDescripcion(), marca, p.getPrecioCosto(), precioNuevo, 1));
                ok++;
            }
        }

        return new PrecioImportPreviewResponse(preview, preview.size(), ok, conflictos, noEncontrados, margen);
    }

    @Transactional
    public PrecioImportResultResponse aplicar(String uploadId, String colSku, String colPrecio,
                                               String proveedor, Set<String> skusExcluidos, String archivo) {
        byte[] contenido = uploads.remove(uploadId);
        if (contenido == null) {
            throw new IllegalArgumentException("Archivo expirado. Volvé a subirlo.");
        }

        BigDecimal margen = obtenerMargen(proveedor);
        BigDecimal multiplicador = BigDecimal.ONE.add(margen.divide(BigDecimal.valueOf(100)));

        List<String[]> filasCsv = parsearFilas(contenido, colSku, colPrecio);

        Set<String> skusUnicos = filasCsv.stream()
                .map(f -> f[0])
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        Map<String, List<Producto>> productosPorSku = productoRepository.findBySkuIn(skusUnicos)
                .stream()
                .collect(Collectors.groupingBy(Producto::getSku));

        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setProveedor(proveedor);
        batch.setFuente("CSV_IMPORT");
        batch.setArchivo(archivo);
        batchRepo.save(batch);

        int aplicados = 0, omitidos = 0, conflictos = 0;
        List<Producto> modificados = new ArrayList<>();
        List<HistorialPrecio> historiales = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (String[] fila : filasCsv) {
            String sku = fila[0];
            BigDecimal precioCsv = parsearPrecio(fila[1]);
            if (sku == null || sku.isBlank() || precioCsv == null) continue;

            if (skusExcluidos != null && skusExcluidos.contains(sku)) {
                omitidos++;
                continue;
            }

            List<Producto> matches = productosPorSku.getOrDefault(sku, List.of());
            if (matches.size() != 1) {
                if (matches.size() > 1) conflictos++;
                else omitidos++;
                continue;
            }

            Producto p = matches.getFirst();
            BigDecimal precioVenta = precioCsv.multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);

            HistorialPrecio h = new HistorialPrecio();
            h.setProducto(p);
            h.setBatch(batch);
            h.setPrecioCostoAnterior(p.getPrecioCosto());
            h.setPrecioVentaAnterior(p.getPrecioVenta());
            h.setPrecioCostoNuevo(precioCsv);
            h.setPrecioVentaNuevo(precioVenta);
            h.setMargenAplicado(margen);
            historiales.add(h);

            p.setPrecioCosto(precioCsv);
            p.setPrecioVenta(precioVenta);
            p.setPrecioActualizadoEn(ahora);
            modificados.add(p);
            aplicados++;
        }

        if (!modificados.isEmpty()) productoRepository.saveAll(modificados);
        if (!historiales.isEmpty()) historialRepo.saveAll(historiales);

        batch.setTotal(aplicados + omitidos + conflictos);
        batch.setAplicados(aplicados);
        batch.setOmitidos(omitidos);
        batch.setConflictos(conflictos);
        batchRepo.save(batch);

        String mensaje = String.format("Importación completada: %d aplicados, %d omitidos, %d conflictos (margen %.2f%%)",
                aplicados, omitidos, conflictos, margen);
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

    @Transactional
    public ImportPrecioBatch crearBatchApiSync(String proveedor, int total, int aplicados) {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setProveedor(proveedor);
        batch.setFuente("API_SYNC");
        batch.setTotal(total);
        batch.setAplicados(aplicados);
        return batchRepo.save(batch);
    }

    @Transactional
    public void guardarHistorial(List<HistorialPrecio> historiales) {
        if (!historiales.isEmpty()) historialRepo.saveAll(historiales);
    }

    private BigDecimal obtenerMargen(String proveedor) {
        return configuracionRepo.findByProveedorIgnoreCase(proveedor)
                .map(ConfiguracionPrecio::getMargen)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay configuración de margen para el proveedor: " + proveedor));
    }

    private List<String[]> parsearFilas(byte[] contenido, String colSku, String colPrecio) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(contenido), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, csvFormat())) {

            List<String[]> filas = new ArrayList<>();
            for (CSVRecord record : parser) {
                String sku = valor(record, colSku);
                String precio = valor(record, colPrecio);
                filas.add(new String[]{sku, precio});
            }
            return filas;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al leer el CSV: " + e.getMessage());
        }
    }

    private String valor(CSVRecord record, String columna) {
        try {
            String v = record.get(columna);
            return v == null || v.isBlank() ? null : v.trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal parsearPrecio(String valor) {
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
