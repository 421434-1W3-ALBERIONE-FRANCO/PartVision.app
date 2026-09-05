package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.HistorialPrecio;
import com.partvision.pricing.domain.ImportPrecioBatch;
import com.partvision.pricing.dto.PrecioBatchResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportProgresoResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrecioImportServiceTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private ConfiguracionPrecioRepository configuracionRepo;
    @Mock private ImportPrecioBatchRepository batchRepo;
    @Mock private HistorialPrecioRepository historialRepo;

    private PrecioImportService service;

    @BeforeEach
    void setUp() {
        service = new PrecioImportService(productoRepository, configuracionRepo, batchRepo, historialRepo);
    }

    // --- detectarColumnas ---

    @Test
    void detectarColumnas_csv_retornaColumnasYTotalFilas() {
        byte[] csv = "sku,precio,descripcion\nA1,100.50,Filtro\nA2,200,Correa\n".getBytes(StandardCharsets.UTF_8);

        PrecioImportColumnasResponse resp = service.detectarColumnas(csv, "precios.csv");

        assertThat(resp.columnas()).containsExactly("sku", "precio", "descripcion");
        assertThat(resp.totalFilas()).isEqualTo(2);
        assertThat(resp.uploadId()).isNotBlank();
    }

    @Test
    void detectarColumnas_csvSoloHeader_retornaCeroFilas() {
        byte[] csv = "sku,precio\n".getBytes(StandardCharsets.UTF_8);

        PrecioImportColumnasResponse resp = service.detectarColumnas(csv, "solo_header.csv");

        assertThat(resp.columnas()).containsExactly("sku", "precio");
        assertThat(resp.totalFilas()).isEqualTo(0);
    }

    @Test
    void detectarColumnas_excel_retornaColumnas() {
        byte[] xls = buildMinimalXlsx();

        PrecioImportColumnasResponse resp = service.detectarColumnas(xls, "precios.xlsx");

        assertThat(resp.columnas()).contains("Codigo", "Precio");
        assertThat(resp.uploadId()).isNotBlank();
    }

    // --- preview ---

    @Test
    void preview_conMatch_retornaOK() {
        byte[] csv = "sku,precio\nSKU1,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        Producto p = buildProducto(1L, "SKU1", null);
        ConfiguracionPrecio config = buildConfig("PROV", new BigDecimal("20"));

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.ok()).isEqualTo(1);
        assertThat(resp.filas()).hasSize(1);
        assertThat(resp.filas().getFirst().estado()).isEqualTo("OK");
        assertThat(resp.filas().getFirst().precioNuevoCalculado()).isEqualByComparingTo("120.00");
    }

    @Test
    void preview_sinMatch_retornaNoEncontrado() {
        byte[] csv = "sku,precio\nXXX,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        ConfiguracionPrecio config = buildConfig("PROV", new BigDecimal("10"));

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.noEncontrados()).isEqualTo(1);
        assertThat(resp.filas().getFirst().estado()).isEqualTo("NO_ENCONTRADO");
    }

    @Test
    void preview_multipleMatches_retornaConflicto() {
        byte[] csv = "sku,precio\nSKU1,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        Producto p1 = buildProducto(1L, "SKU1", "Marca A");
        Producto p2 = buildProducto(2L, "SKU1", "Marca B");
        ConfiguracionPrecio config = buildConfig("PROV", new BigDecimal("10"));

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p1, p2));
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.conflictos()).isEqualTo(1);
        assertThat(resp.filas().getFirst().estado()).isEqualTo("CONFLICTO");
    }

    @Test
    void preview_uploadNoExiste_lanzaExcepcion() {
        assertThatThrownBy(() -> service.preview("no-existe", "sku", "precio", "PROV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archivo no encontrado");
    }

    @Test
    void preview_proveedorSinConfig_lanzaExcepcion() {
        byte[] csv = "sku,precio\nSKU1,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        when(configuracionRepo.findByProveedorIgnoreCase("DESCONOCIDO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(cols.uploadId(), "sku", "precio", "DESCONOCIDO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay configuración de margen");
    }

    // --- validarAplicar ---

    @Test
    void validarAplicar_uploadNoExiste_lanzaExcepcion() {
        assertThatThrownBy(() -> service.validarAplicar("no-existe", "PROV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archivo expirado");
    }

    @Test
    void validarAplicar_sinConfig_lanzaExcepcion() {
        byte[] csv = "sku,precio\nSKU1,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        when(configuracionRepo.findByProveedorIgnoreCase("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validarAplicar(cols.uploadId(), "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- ejecutarImportAsync (calls aplicar internally) ---

    @Test
    void ejecutarImportAsync_aplicaPreciosYGuardaHistorial() {
        byte[] csv = "sku,precio\nSKU1,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");
        service.iniciarImport();

        Producto p = buildProducto(1L, "SKU1", null);
        p.setPrecioCosto(new BigDecimal("80"));
        p.setPrecioVenta(new BigDecimal("96"));

        ConfiguracionPrecio config = buildConfig("PROV", new BigDecimal("20"));

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));
        when(batchRepo.save(any(ImportPrecioBatch.class))).thenAnswer(inv -> {
            ImportPrecioBatch b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        service.ejecutarImportAsync(cols.uploadId(), "sku", "precio", "PROV", Set.of(), "test.csv");

        verify(productoRepository).saveAll(any());
        verify(historialRepo).saveAll(any());
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100");
        assertThat(p.getPrecioVenta()).isEqualByComparingTo("120.00");
    }

    @Test
    void ejecutarImportAsync_excluidos_omiteSkus() {
        byte[] csv = "sku,precio\nSKU1,100\nSKU2,200\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");
        service.iniciarImport();

        Producto p1 = buildProducto(1L, "SKU1", null);
        Producto p2 = buildProducto(2L, "SKU2", null);

        ConfiguracionPrecio config = buildConfig("PROV", new BigDecimal("10"));

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p1, p2));
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));
        when(batchRepo.save(any(ImportPrecioBatch.class))).thenAnswer(inv -> {
            ImportPrecioBatch b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        service.ejecutarImportAsync(cols.uploadId(), "sku", "precio", "PROV", Set.of("SKU2"), "test.csv");

        assertThat(p1.getPrecioCosto()).isEqualByComparingTo("100");
        assertThat(p2.getPrecioCosto()).isNull();
    }

    @Test
    void ejecutarImportAsync_archivoExpirado_guardaError() {
        service.iniciarImport();

        service.ejecutarImportAsync("no-existe", "sku", "precio", "PROV", Set.of(), "test.csv");

        PrecioImportProgresoResponse progreso = service.getProgresoImport();
        assertThat(progreso.importando()).isFalse();
        assertThat(progreso.ultimoResultado()).isNotNull();
        assertThat(progreso.ultimoResultado().mensaje()).contains("Error");
    }

    // --- iniciarImport / getProgreso ---

    @Test
    void iniciarImport_primeraVez_retornaTrue() {
        assertThat(service.iniciarImport()).isTrue();
    }

    @Test
    void getProgresoImport_sinImportar_retornaIdle() {
        PrecioImportProgresoResponse resp = service.getProgresoImport();
        assertThat(resp.importando()).isFalse();
        assertThat(resp.progreso()).isEqualTo(0);
    }

    // --- rollback ---

    @Test
    void rollback_reviertePrecios() {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setId(1L);
        batch.setProveedor("PROV");
        batch.setFuente("CSV_IMPORT");
        batch.setEstado("APLICADO");

        Producto p = buildProducto(1L, "SKU1", null);
        p.setPrecioCosto(new BigDecimal("100"));
        p.setPrecioVenta(new BigDecimal("120"));

        HistorialPrecio h = new HistorialPrecio();
        h.setProducto(p);
        h.setPrecioCostoAnterior(new BigDecimal("80"));
        h.setPrecioVentaAnterior(new BigDecimal("96"));
        h.setPrecioCostoNuevo(new BigDecimal("100"));
        h.setPrecioVentaNuevo(new BigDecimal("120"));

        when(batchRepo.findById(1L)).thenReturn(Optional.of(batch));
        when(historialRepo.findByBatchId(1L)).thenReturn(List.of(h));

        PrecioBatchResponse resp = service.rollback(1L);

        assertThat(resp.estado()).isEqualTo("REVERTIDO");
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("80");
        assertThat(p.getPrecioVenta()).isEqualByComparingTo("96");
        verify(productoRepository).saveAll(any());
    }

    @Test
    void rollback_yaRevertido_lanzaExcepcion() {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setId(1L);
        batch.setEstado("REVERTIDO");

        when(batchRepo.findById(1L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> service.rollback(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya fue revertido");
    }

    @Test
    void rollback_batchNoExiste_lanzaExcepcion() {
        when(batchRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollback(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch no encontrado");
    }

    @Test
    void rollback_precioModificadoDespues_noRevierte() {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setId(1L);
        batch.setProveedor("PROV");
        batch.setFuente("CSV_IMPORT");
        batch.setEstado("APLICADO");

        Producto p = buildProducto(1L, "SKU1", null);
        p.setPrecioCosto(new BigDecimal("999"));
        p.setPrecioVenta(new BigDecimal("120"));

        HistorialPrecio h = new HistorialPrecio();
        h.setProducto(p);
        h.setPrecioCostoAnterior(new BigDecimal("80"));
        h.setPrecioVentaAnterior(new BigDecimal("96"));
        h.setPrecioCostoNuevo(new BigDecimal("100"));
        h.setPrecioVentaNuevo(new BigDecimal("120"));

        when(batchRepo.findById(1L)).thenReturn(Optional.of(batch));
        when(historialRepo.findByBatchId(1L)).thenReturn(List.of(h));

        service.rollback(1L);

        assertThat(p.getPrecioCosto()).isEqualByComparingTo("999");
        verify(productoRepository, never()).saveAll(any());
    }

    // --- listarBatches ---

    @Test
    void listarBatches_retornaListaConvertida() {
        ImportPrecioBatch b = new ImportPrecioBatch();
        b.setId(1L);
        b.setProveedor("PROV");
        b.setFuente("CSV_IMPORT");
        b.setEstado("APLICADO");

        when(batchRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(b));

        List<PrecioBatchResponse> result = service.listarBatches();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().proveedor()).isEqualTo("PROV");
    }

    // --- parsearPrecio edge cases (via preview) ---

    @Test
    void preview_precioConSignoPeso_parseaCorrecto() {
        byte[] csv = "sku,precio\nSKU1,\"$100.50\"\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        Producto p = buildProducto(1L, "SKU1", null);
        ConfiguracionPrecio config = buildConfig("PROV", BigDecimal.ZERO);

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.filas()).hasSize(1);
        assertThat(resp.filas().getFirst().precioCostoCsv()).isEqualByComparingTo("100.50");
    }

    @Test
    void preview_precioInvalido_filaIgnorada() {
        byte[] csv = "sku,precio\nSKU1,abc\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        ConfiguracionPrecio config = buildConfig("PROV", BigDecimal.ZERO);

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.filas()).isEmpty();
    }

    @Test
    void preview_skuBlanco_filaIgnorada() {
        byte[] csv = "sku,precio\n,100\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse cols = service.detectarColumnas(csv, "test.csv");

        ConfiguracionPrecio config = buildConfig("PROV", BigDecimal.ZERO);

        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        PrecioImportPreviewResponse resp = service.preview(cols.uploadId(), "sku", "precio", "PROV");

        assertThat(resp.filas()).isEmpty();
    }

    // --- Excel cellToString branches ---

    @Test
    void detectarColumnas_excelConBooleanos_parseaCorrecto() {
        byte[] xls = buildXlsxWithBooleanAndFormula();

        PrecioImportColumnasResponse resp = service.detectarColumnas(xls, "booleans.xlsx");

        assertThat(resp.columnas()).contains("Codigo", "Activo");
    }

    @Test
    void preview_excel_columnaSkuNoExiste_lanzaExcepcion() {
        byte[] xls = buildMinimalXlsx();
        PrecioImportColumnasResponse cols = service.detectarColumnas(xls, "test.xlsx");

        ConfiguracionPrecio config = buildConfig("PROV", BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.preview(cols.uploadId(), "NoExiste", "Precio", "PROV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Columna SKU");
    }

    @Test
    void preview_excel_columnaPrecioNoExiste_lanzaExcepcion() {
        byte[] xls = buildMinimalXlsx();
        PrecioImportColumnasResponse cols = service.detectarColumnas(xls, "test.xlsx");

        ConfiguracionPrecio config = buildConfig("PROV", BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase("PROV")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.preview(cols.uploadId(), "Codigo", "NoExiste", "PROV"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Columna precio");
    }

    // --- esArchivoExcel ---

    @Test
    void detectarColumnas_archivoXls_tratadoComoExcel() {
        byte[] xls = buildMinimalXlsx();
        PrecioImportColumnasResponse resp = service.detectarColumnas(xls, "data.xls");
        assertThat(resp.columnas()).contains("Codigo", "Precio");
    }

    @Test
    void detectarColumnas_nombreNull_tratadoComoCsv() {
        byte[] csv = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        PrecioImportColumnasResponse resp = service.detectarColumnas(csv, null);
        assertThat(resp.columnas()).containsExactly("a", "b");
    }

    // --- Helpers ---

    private Producto buildProducto(Long id, String sku, String marcaNombre) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        if (marcaNombre != null) {
            Marca m = new Marca();
            m.setNombre(marcaNombre);
            p.setMarca(m);
        }
        return p;
    }

    private ConfiguracionPrecio buildConfig(String proveedor, BigDecimal margen) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor(proveedor);
        c.setMargen(margen);
        c.setActivo(true);
        return c;
    }

    private byte[] buildXlsxWithBooleanAndFormula() {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Datos");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Activo");
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("SKU1");
            row1.createCell(1).setCellValue(true);
            var out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] buildMinimalXlsx() {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            var sheet = wb.createSheet("Datos");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("SKU1");
            row1.createCell(1).setCellValue(100.0);
            var out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
