package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.HistorialPrecio;
import com.partvision.pricing.domain.ImportPrecioBatch;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportProgresoResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La aplicacion de precios: que se omite, que se cuenta como conflicto, y los bordes de
 * lectura de celdas. Todo lo que decide si un precio se pisa o no.
 */
@ExtendWith(MockitoExtension.class)
class PrecioImportServiceAplicarTest {

    @Mock private ProductoRepository productoRepository;
    @Mock private ConfiguracionPrecioRepository configuracionRepo;
    @Mock private ImportPrecioBatchRepository batchRepo;
    @Mock private HistorialPrecioRepository historialRepo;
    @Mock private ProductoBulkImporter bulkImporter;

    private PrecioImportService service;

    @BeforeEach
    void setUp() {
        service = new PrecioImportService(productoRepository, configuracionRepo, batchRepo,
                historialRepo, bulkImporter);
    }

    private Producto producto(Long id, String sku, String proveedor) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        p.setProveedor(proveedor);
        Marca m = new Marca();
        m.setNombre("Mahle");
        p.setMarca(m);
        return p;
    }

    private void configurar(String proveedor, String margen) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor(proveedor);
        c.setMargen(new BigDecimal(margen));
        c.setAjusteLista(BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase(proveedor)).thenReturn(Optional.of(c));
    }

    private void batchConId() {
        when(batchRepo.save(any(ImportPrecioBatch.class))).thenAnswer(inv -> {
            ImportPrecioBatch b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });
    }

    private String subir(String csv) {
        PrecioImportColumnasResponse cols =
                service.detectarColumnas(csv.getBytes(StandardCharsets.UTF_8), "precios.csv");
        service.iniciarImport();
        return cols.uploadId();
    }

    // --- aplicar ---

    /** Sin lista de exclusiones (null) se aplica todo: no puede confundirse con "excluir todo". */
    @Test
    void aplicar_sinListaDeExcluidos_aplicaTodo() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\nSKU1,100\n");
        Producto p = producto(1L, "SKU1", "ADS");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", null, "precios.csv");

        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100.00");
        assertThat(p.getPrecioVenta()).isEqualByComparingTo("110.00");
    }

    @Test
    void aplicar_filasSinSkuOSinPrecio_seIgnoran() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\n\"\",100\nSKU1,no-es-precio\nSKU1,100\n");
        Producto p = producto(1L, "SKU1", "ADS");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");

        PrecioImportProgresoResponse progreso = service.getProgresoImport();
        assertThat(progreso.ultimoResultado().aplicados()).isEqualTo(1);
        assertThat(progreso.ultimoResultado().total()).isEqualTo(1);
    }

    @Test
    void aplicar_skuConDosProductosDelMismoProveedor_cuentaConflicto() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\nSKU1,100\n");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "SKU1", "ADS"), producto(2L, "SKU1", "ADS")));

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");

        assertThat(service.getProgresoImport().ultimoResultado().conflictos()).isEqualTo(1);
        verify(productoRepository, never()).saveAll(anyCollection());
    }

    @Test
    void aplicar_skuQueNoExiste_cuentaOmitido() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\nSKU-FANTASMA,100\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");

        assertThat(service.getProgresoImport().ultimoResultado().omitidos()).isEqualTo(1);
        verify(historialRepo, never()).saveAll(anyCollection());
    }

    /** El progreso queda reseteado y el flag de "importando" liberado pase lo que pase. */
    @Test
    void aplicar_liberaElFlagDeImportando() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\nSKU1,100\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "SKU1", "ADS")));

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");

        assertThat(service.getProgresoImport().importando()).isFalse();
        assertThat(service.iniciarImport()).isTrue();
    }

    /** Un mismo archivo no se puede aplicar dos veces: el upload se consume al aplicar. */
    @Test
    void aplicar_dosVeces_laSegundaFalla() {
        configurar("ADS", "10");
        batchConId();
        String id = subir("sku,precio\nSKU1,100\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "SKU1", "ADS")));

        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");
        service.ejecutarImportAsync(id, "sku", "precio", "ADS", Set.of(), "precios.csv");

        assertThat(service.getProgresoImport().ultimoResultado().mensaje()).contains("Error");
    }

    // --- lotes de SKU ---

    /**
     * Mas de 10.000 SKU van en tandas: el driver JDBC no admite mas de 65.535 parametros en
     * una consulta y una lista de precios real trae 67.000 filas.
     */
    @Test
    void masDeDiezMilSkus_seConsultanEnLotes() {
        configurar("ADS", "10");
        StringBuilder csv = new StringBuilder("sku,precio\n");
        for (int i = 0; i < 10_005; i++) {
            csv.append("SKU").append(i).append(",100\n");
        }
        String id = subir(csv.toString());
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.total()).isEqualTo(10_005);
        verify(productoRepository, times(2)).findBySkuIn(any());
    }

    /** El preview manda una muestra al navegador, no 67.000 filas que lo congelen. */
    @Test
    void preview_recortaLaMuestraPeroCuentaElTotal() {
        configurar("ADS", "10");
        StringBuilder csv = new StringBuilder("sku,precio\n");
        for (int i = 0; i < 600; i++) {
            csv.append("SKU").append(i).append(",100\n");
        }
        String id = subir(csv.toString());
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.total()).isEqualTo(600);
        assertThat(resp.filas()).hasSize(500);
        assertThat(resp.detalleNoEncontrados()).hasSize(600);
    }

    // --- rollback ---

    @Test
    void rollback_sinHistorial_marcaElBatchIgual() {
        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setId(9L);
        batch.setProveedor("ADS");
        when(batchRepo.findById(9L)).thenReturn(Optional.of(batch));
        when(historialRepo.findByBatchId(9L)).thenReturn(List.of());
        when(batchRepo.save(any(ImportPrecioBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rollback(9L);

        assertThat(batch.getEstado()).isEqualTo("REVERTIDO");
        verify(productoRepository, never()).saveAll(anyCollection());
    }

    @Test
    void rollback_precioSoloDeVentaModificado_noRevierte() {
        Producto p = producto(1L, "SKU1", "ADS");
        p.setPrecioCosto(new BigDecimal("100.00"));
        p.setPrecioVenta(new BigDecimal("999.00"));

        HistorialPrecio h = new HistorialPrecio();
        h.setProducto(p);
        h.setPrecioCostoAnterior(new BigDecimal("80.00"));
        h.setPrecioVentaAnterior(new BigDecimal("96.00"));
        h.setPrecioCostoNuevo(new BigDecimal("100.00"));
        h.setPrecioVentaNuevo(new BigDecimal("110.00"));

        ImportPrecioBatch batch = new ImportPrecioBatch();
        batch.setId(9L);
        when(batchRepo.findById(9L)).thenReturn(Optional.of(batch));
        when(historialRepo.findByBatchId(9L)).thenReturn(List.of(h));
        when(batchRepo.save(any(ImportPrecioBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        service.rollback(9L);

        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100.00");
        verify(productoRepository, never()).saveAll(anyCollection());
    }

    // --- lectura de celdas de Excel ---

    @Test
    void excel_tiposDeCelda_seLeenComoTexto() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            header.createCell(2).setCellValue("Descripcion");

            Row entero = sheet.createRow(1);
            entero.createCell(0).setCellValue("SKU-ENTERO");
            entero.createCell(1).setCellValue(100);
            entero.createCell(2).setCellValue(true);

            Row decimal = sheet.createRow(2);
            decimal.createCell(0).setCellValue("SKU-DECIMAL");
            decimal.createCell(1).setCellValue(100.55);
            decimal.createCell(2).setCellFormula("1+1");

            Row vacia = sheet.createRow(3);
            vacia.createCell(0).setCellValue("SKU-VACIO");
            vacia.createCell(1).setCellValue(50);
            vacia.createCell(2, org.apache.poi.ss.usermodel.CellType.BLANK);
        });
        configurar("ADS", "0");
        PrecioImportColumnasResponse cols = service.detectarColumnas(xlsx, "lista.xlsx");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp =
                service.preview(cols.uploadId(), "Codigo", "Precio", "ADS");

        assertThat(resp.total()).isEqualTo(3);
        List<String> descripciones = resp.detalleNoEncontrados().stream()
                .map(PrecioImportPreviewResponse.FilaNoEncontrada::descripcion)
                .toList();
        // La formula se lee por su valor cacheado: POI no la recalcula al abrir el archivo.
        assertThat(descripciones).containsExactly("true", "0.0", null);
        assertThat(resp.detalleNoEncontrados().get(0).precio()).isEqualByComparingTo("100.00");
        assertThat(resp.detalleNoEncontrados().get(1).precio()).isEqualByComparingTo("100.55");
    }

    /** Una hoja con filas en blanco intercaladas no tiene que correr el encabezado. */
    @Test
    void excel_conFilasNulasAntesDelHeader_igualLoEncuentra() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            Row fila = sheet.createRow(3);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100);
        });

        PrecioImportColumnasResponse cols = service.detectarColumnas(xlsx, "lista.xlsx");

        assertThat(cols.columnas()).containsExactly("Codigo", "Precio");
    }

    private interface Armador {
        void armar(Sheet sheet);
    }

    private byte[] excel(Armador armador) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Datos");
            armador.armar(sheet);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Cell> celdas(Row row) {
        List<Cell> result = new ArrayList<>();
        row.forEach(result::add);
        return result;
    }
}
