package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.imports.service.ProductoImporter;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.dto.PrecioAltaFaltantesResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre las partes del import de precios que no toca el test principal: el alta de SKU
 * faltantes, la desambiguacion por proveedor, el vencimiento de los archivos subidos y los
 * bordes de lectura de CSV y Excel.
 */
@ExtendWith(MockitoExtension.class)
class PrecioImportServiceCoberturaTest {

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

    // --- helpers ---

    private String subir(byte[] contenido, String nombre) {
        PrecioImportColumnasResponse resp = service.detectarColumnas(contenido, nombre);
        return resp.uploadId();
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

    private void configurarTarifa(String proveedor, String margen, String ajuste) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor(proveedor);
        c.setMargen(new BigDecimal(margen));
        c.setAjusteLista(ajuste == null ? null : new BigDecimal(ajuste));
        when(configuracionRepo.findByProveedorIgnoreCase(proveedor)).thenReturn(Optional.of(c));
    }

    private byte[] csv(String contenido) {
        return contenido.getBytes(StandardCharsets.UTF_8);
    }

    // --- alta de SKU faltantes ---

    @Test
    void crearFaltantes_uploadInexistente_lanzaExcepcion() {
        assertThatThrownBy(() -> service.crearFaltantes("no-existe", "sku", "precio", "ADS", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Volvé a subirlo");
    }

    @Test
    void crearFaltantes_sinSeleccion_creaTodosLosQueNoExisten() {
        String id = subir(csv("sku,precio,descripcion,marca\nA1,100,Filtro aceite,Mahle\nA2,200,Correa,Gates\n"),
                "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "A1", "ADS")));
        doAnswer(inv -> {
            ImportJob job = inv.getArgument(1);
            job.marcarImportada();
            job.completar();
            return null;
        }).when(bulkImporter).importar(any(), any());

        PrecioAltaFaltantesResponse resp = service.crearFaltantes(id, "sku", "precio", "ADS", null);

        assertThat(resp.creados()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).sku()).isEqualTo("A2");
        assertThat(captor.getValue().get(0).descripcion()).isEqualTo("Correa");
        assertThat(captor.getValue().get(0).marca()).isEqualTo("Gates");
        assertThat(captor.getValue().get(0).proveedor()).isEqualTo("ADS");
    }

    @Test
    void crearFaltantes_conSeleccion_ignoraLosDemas() {
        String id = subir(csv("sku,precio\nA1,100\nA2,200\nA3,300\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of("A2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).sku()).isEqualTo("A2");
    }

    /** Sin columna de descripcion, el SKU hace de descripcion para no crear productos sin nombre. */
    @Test
    void crearFaltantes_sinDescripcion_usaElSku() {
        String id = subir(csv("sku,precio\nA9,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue().get(0).descripcion()).isEqualTo("A9");
    }

    @Test
    void crearFaltantes_descripcionEnBlanco_usaElSku() {
        String id = subir(csv("sku,precio,descripcion\nA9,100,\"   \"\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue().get(0).descripcion()).isEqualTo("A9");
    }

    @Test
    void crearFaltantes_skuRepetidoEnElArchivo_seCreaUnaSolaVez() {
        String id = subir(csv("sku,precio\nA1,100\nA1,150\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void crearFaltantes_sinFilasConSku_noLlamaAlImportador() {
        String id = subir(csv("sku,precio\n\"\",100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioAltaFaltantesResponse resp = service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        assertThat(resp.creados()).isZero();
        assertThat(resp.mensaje()).contains("No había productos nuevos");
        verify(bulkImporter, never()).importar(any(), any());
    }

    @Test
    void crearFaltantes_todosExisten_noLlamaAlImportador() {
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "A1", "ADS")));

        PrecioAltaFaltantesResponse resp = service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        assertThat(resp.creados()).isZero();
        verify(bulkImporter, never()).importar(any(), any());
    }

    // --- desambiguacion por proveedor ---

    @Test
    void preview_mismoSkuEnDosProveedores_desempataPorProveedor() {
        configurarTarifa("ADS", "10", "0");
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "A1", "ADS"), producto(2L, "A1", "EGSA")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.ok()).isEqualTo(1);
        assertThat(resp.conflictos()).isZero();
        assertThat(resp.filas().get(0).productoId()).isEqualTo(1L);
    }

    @Test
    void preview_ningunoDelProveedor_quedaComoConflicto() {
        configurarTarifa("ADS", "10", "0");
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "A1", "OTRO"), producto(2L, "A1", null)));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.conflictos()).isEqualTo(1);
    }

    @Test
    void preview_variosDelMismoProveedor_quedaComoConflicto() {
        configurarTarifa("ADS", "10", "0");
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "A1", "ADS"), producto(2L, "A1", "ads")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.conflictos()).isEqualTo(1);
    }

    @Test
    void preview_sinProveedor_noDesempata() {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor("X");
        c.setMargen(new BigDecimal("10"));
        when(configuracionRepo.findByProveedorIgnoreCase(null)).thenReturn(Optional.of(c));
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "A1", "ADS"), producto(2L, "A1", "EGSA")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", null);

        assertThat(resp.conflictos()).isEqualTo(1);
    }

    // --- tarifa ---

    /** ajuste_lista nulo en la base (configuraciones viejas) se toma como cero. */
    @Test
    void preview_ajusteListaNulo_seTomaComoCero() {
        configurarTarifa("ADS", "10", null);
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "A1", "ADS")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.filas().get(0).precioCostoCsv()).isEqualByComparingTo("100.00");
        assertThat(resp.filas().get(0).precioNuevoCalculado()).isEqualByComparingTo("110.00");
    }

    @Test
    void preview_conAjusteLista_elCostoSubeAntesDelMargen() {
        configurarTarifa("ADS", "10", "22.5");
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "A1", "ADS")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.filas().get(0).precioCostoCsv()).isEqualByComparingTo("122.50");
        assertThat(resp.filas().get(0).precioNuevoCalculado()).isEqualByComparingTo("134.75");
    }

    // --- vencimiento de archivos subidos ---

    /** Sin esto el heap crece con cada archivo abandonado a mitad del flujo. */
    @Test
    void purgarUploads_dejaComoMuchoElMaximo() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(subir(csv("sku,precio\nA" + i + ",100\n"), "precios.csv"));
        }

        service.purgarUploads();

        // El tope es lo que importa: con cual se queda entre subidas del mismo milisegundo
        // es indistinto (el orden por antiguedad empata y no hay preferencia definida).
        long vivos = ids.stream().filter(this::sigueVivo).count();
        assertThat(vivos).isEqualTo(4);
    }

    private boolean sigueVivo(String uploadId) {
        try {
            service.validarAplicar(uploadId, "ADS");
            return true;
        } catch (IllegalArgumentException e) {
            return !e.getMessage().contains("expirado");
        }
    }

    // --- lectura de CSV ---

    @Test
    void preview_columnaInexistenteEnCsv_dejaElValorNulo() {
        configurarTarifa("ADS", "10", "0");
        String id = subir(csv("sku,precio\nA1,100\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "columna-que-no-esta", "ADS");

        assertThat(resp.total()).isZero();
    }

    /** Un archivo ilegible no puede quedar ocupando memoria: el upload se descarta. */
    @Test
    void detectarColumnas_csvConComillasSinCerrar_descartaElUpload() {
        byte[] roto = csv("sku,precio\n\"A1,100\n");

        assertThatThrownBy(() -> service.detectarColumnas(roto, "precios.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se pudo leer el archivo");
    }

    @Test
    void detectarColumnas_csvConColumnaDetalle_laReconoceComoDescripcion() {
        String id = subir(csv("sku,precio,Detalle\nA1,100,Filtro\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue().get(0).descripcion()).isEqualTo("Filtro");
    }

    @Test
    void detectarColumnas_csvConDescription_enIngles_tambienSeReconoce() {
        String id = subir(csv("sku,precio,Description\nA1,100,Filter\n"), "precios.csv");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        service.crearFaltantes(id, "sku", "precio", "ADS", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> captor = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(captor.capture(), any());
        assertThat(captor.getValue().get(0).descripcion()).isEqualTo("Filter");
    }

    // --- lectura de Excel ---

    @Test
    void detectarColumnas_excelConTituloArriba_tomaLaFilaDeEncabezados() {
        byte[] xlsx = excel(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("LISTA DE PRECIOS ADS S.A.");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            Row fila = sheet.createRow(2);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100);
        });

        PrecioImportColumnasResponse resp = service.detectarColumnas(xlsx, "lista.xlsx");

        assertThat(resp.columnas()).containsExactly("Codigo", "Precio");
        assertThat(resp.totalFilas()).isEqualTo(1);
    }

    @Test
    void preview_excelConDescripcionYMarca_lasLee() {
        configurarTarifa("ADS", "10", "0");
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            header.createCell(2).setCellValue("Descripcion");
            header.createCell(3).setCellValue("Marca");
            Row fila = sheet.createRow(1);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100);
            fila.createCell(2).setCellValue("Filtro aceite");
            fila.createCell(3).setCellValue("Mahle");
        });
        String id = subir(xlsx, "lista.xlsx");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "Codigo", "Precio", "ADS");

        assertThat(resp.noEncontrados()).isEqualTo(1);
        assertThat(resp.detalleNoEncontrados().get(0).descripcion()).isEqualTo("Filtro aceite");
    }

    @Test
    void preview_excelConFilasVacias_lasSaltea() {
        configurarTarifa("ADS", "10", "0");
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            sheet.createRow(1).createCell(0).setCellValue("");
            Row fila = sheet.createRow(2);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100.75);
        });
        String id = subir(xlsx, "lista.xlsx");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "Codigo", "Precio", "ADS");

        assertThat(resp.total()).isEqualTo(1);
    }

    @Test
    void detectarColumnas_excelSinFilas_lanzaExcepcion() {
        byte[] xlsx = excel(sheet -> { });

        assertThatThrownBy(() -> service.detectarColumnas(xlsx, "vacio.xlsx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectarColumnas_excelConUnaSolaColumna_noEncuentraEncabezados() {
        byte[] xlsx = excel(sheet -> sheet.createRow(0).createCell(0).setCellValue("solo-titulo"));

        PrecioImportColumnasResponse resp = service.detectarColumnas(xlsx, "lista.xlsx");

        assertThat(resp.columnas()).containsExactly("solo-titulo");
    }

    @Test
    void detectarColumnas_archivoQueNoEsExcel_conNombreXlsx_lanzaExcepcion() {
        assertThatThrownBy(() -> service.detectarColumnas(csv("no soy un excel"), "mentira.xlsx"))
                .isInstanceOf(RuntimeException.class);
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
}
