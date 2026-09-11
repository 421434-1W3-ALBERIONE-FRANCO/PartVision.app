package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.pricing.domain.ConfiguracionPrecio;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Archivos mal formados o con columnas que no son las que se eligieron. Todos estos casos
 * llegan de listas reales de proveedores, y ninguno puede terminar en un 500.
 */
@ExtendWith(MockitoExtension.class)
class PrecioImportServiceBordesTest {

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

    private void configurar(String proveedor) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor(proveedor);
        c.setMargen(new BigDecimal("10"));
        c.setAjusteLista(BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase(proveedor)).thenReturn(Optional.of(c));
    }

    private String subirCsv(String csv) {
        return service.detectarColumnas(csv.getBytes(StandardCharsets.UTF_8), "precios.csv").uploadId();
    }

    private Producto producto(Long id, String sku, String marcaNombre) {
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

    /** Si el usuario elige mal la columna de SKU, ninguna fila tiene codigo: cero, no error. */
    @Test
    void preview_columnaSkuInexistente_ningunaFilaCuenta() {
        configurar("ADS");
        String id = subirCsv("sku,precio\nA1,100\nA2,200\n");

        PrecioImportPreviewResponse resp = service.preview(id, "no-existe", "precio", "ADS");

        assertThat(resp.total()).isZero();
        assertThat(resp.noEncontrados()).isZero();
        verify(productoRepository).findBySkuIn(Set.of());
    }

    @Test
    void crearFaltantes_columnaSkuInexistente_noCreaNada() {
        String id = subirCsv("sku,precio\nA1,100\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        assertThat(service.crearFaltantes(id, "no-existe", "precio", "ADS", Set.of()).creados()).isZero();
        verify(bulkImporter, never()).importar(any(), any());
    }

    @Test
    void aplicar_columnaSkuInexistente_noTocaNingunPrecio() {
        configurar("ADS");
        String id = subirCsv("sku,precio\nA1,100\n");
        when(batchRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.iniciarImport();

        service.ejecutarImportAsync(id, "no-existe", "precio", "ADS", Set.of(), "precios.csv");

        assertThat(service.getProgresoImport().ultimoResultado().aplicados()).isZero();
        verify(productoRepository, never()).saveAll(any());
    }

    /** Un conflicto entre productos sin marca no puede romper el armado del mensaje. */
    @Test
    void preview_conflictoEntreProductosSinMarca_describeIgual() {
        configurar("ADS");
        String id = subirCsv("sku,precio\nA1,100\n");
        when(productoRepository.findBySkuIn(any()))
                .thenReturn(List.of(producto(1L, "A1", null), producto(2L, "A1", "Mahle")));

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.conflictos()).isEqualTo(1);
        assertThat(resp.filas().get(0).productoDescripcion())
                .contains("Producto A1")
                .contains("[Mahle]");
    }

    /** Con mas de 10.000 SKU la consulta va en tandas y hay que juntar bien los resultados. */
    @Test
    void masDeDiezMilSkus_juntaLosResultadosDeCadaLote() {
        configurar("ADS");
        StringBuilder csv = new StringBuilder("sku,precio\n");
        for (int i = 0; i < 10_002; i++) {
            csv.append("SKU").append(i).append(",100\n");
        }
        String id = subirCsv(csv.toString());
        when(productoRepository.findBySkuIn(any())).thenAnswer(inv -> {
            java.util.Collection<?> pedidos = inv.getArgument(0);
            List<Producto> encontrados = new ArrayList<>();
            long siguienteId = 1;
            for (Object sku : pedidos) {
                encontrados.add(producto(siguienteId++, (String) sku, "Mahle"));
            }
            return encontrados;
        });

        PrecioImportPreviewResponse resp = service.preview(id, "sku", "precio", "ADS");

        assertThat(resp.total()).isEqualTo(10_002);
        assertThat(resp.ok()).isEqualTo(10_002);
    }

    // --- encabezados de Excel ---

    @Test
    void excel_headerConCeldasVacias_lasIgnora() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("   ");
            header.createCell(2).setCellValue("Precio");
            Row fila = sheet.createRow(1);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(2).setCellValue(100);
        });

        PrecioImportColumnasResponse resp = service.detectarColumnas(xlsx, "lista.xlsx");

        assertThat(resp.columnas()).containsExactly("Codigo", "Precio");
    }

    @Test
    void excel_soloCeldasVacias_avisaQueNoHayColumnas() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("  ");
            header.createCell(1).setCellValue("  ");
        });

        assertThatThrownBy(() -> service.detectarColumnas(xlsx, "lista.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se encontraron columnas");
    }

    /** Una celda de formula que no devuelve numero se lee como texto. */
    @Test
    void excel_formulaDeTexto_seLeeComoTexto() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            header.createCell(2).setCellValue("Descripcion");
            Row fila = sheet.createRow(1);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100);
            org.apache.poi.ss.usermodel.Cell formula = fila.createCell(2);
            formula.setCellFormula("CONCATENATE(\"a\",\"b\")");
            formula.setCellValue("ab");
        });
        configurar("ADS");
        String id = service.detectarColumnas(xlsx, "lista.xlsx").uploadId();
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "Codigo", "Precio", "ADS");

        assertThat(resp.detalleNoEncontrados().get(0).descripcion()).isEqualTo("ab");
    }

    /** El SKU se busca por el nombre exacto de la columna elegida, sin importar mayusculas. */
    @Test
    void excel_columnaElegidaEnOtraCaja_igualLaEncuentra() {
        byte[] xlsx = excel(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Codigo");
            header.createCell(1).setCellValue("Precio");
            Row fila = sheet.createRow(1);
            fila.createCell(0).setCellValue("A1");
            fila.createCell(1).setCellValue(100);
        });
        configurar("ADS");
        String id = service.detectarColumnas(xlsx, "lista.xlsx").uploadId();
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of());

        PrecioImportPreviewResponse resp = service.preview(id, "CODIGO", "precio", "ADS");

        assertThat(resp.total()).isEqualTo(1);
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
