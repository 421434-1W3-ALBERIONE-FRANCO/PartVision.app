package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.imports.service.ProductoImporter;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.ProductosDeLaLista;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.ImportPrecioBatch;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import com.partvision.pricing.repository.HistorialPrecioRepository;
import com.partvision.pricing.repository.ImportPrecioBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas comunes a la importacion manual y a las actualizaciones automaticas: el codigo que
 * el proveedor manda escrito de otra forma, los productos de otro proveedor y los codigos
 * repetidos con marcas distintas. Los casos salen de la lista real de EGSA del 2026-10-15.
 */
@ExtendWith(MockitoExtension.class)
class PrecioImportServiceReglasTest {

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

    private static Producto producto(Long id, String sku, String proveedor, String marca) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        p.setProveedor(proveedor);
        if (marca != null) {
            Marca m = new Marca();
            m.setNombre(marca);
            p.setMarca(m);
        }
        return p;
    }

    private static FilaArchivo fila(String sku, String precio, String marca) {
        return new FilaArchivo(sku, precio, null, marca);
    }

    private void configurar(String proveedor) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(1L);
        c.setProveedor(proveedor);
        c.setMargen(new BigDecimal("10"));
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
        return service.detectarColumnas(csv.getBytes(StandardCharsets.UTF_8), "precios.csv").uploadId();
    }

    private void aplicar(String id, String proveedor) {
        service.iniciarImport();
        service.ejecutarImportAsync(id, "sku", "precio", proveedor, Set.of(), "precios.csv");
    }

    @SuppressWarnings("unchecked")
    private void enElCatalogoConOtroFormato(Producto... productos) {
        when(productoRepository.findByClaveSkuIn(any(Collection.class))).thenReturn(List.of(productos));
    }

    // --- el mismo codigo escrito de otra forma ---

    @Test
    void otroFormato_mismaMarca_loTomaComoElMismoProducto() {
        Producto pk2000 = producto(1L, "PK2000", "EGSA", "PACKSON");
        enElCatalogoConOtroFormato(pk2000);

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-2000", "100", "Packson")), "EGSA");

        assertThat(r.porCodigo().get("PK-2000")).containsExactly(pk2000);
        assertThat(r.conOtroFormato()).containsEntry("PK-2000", "PK2000");
    }

    @Test
    void otroFormato_otraMarca_noLoToma() {
        enElCatalogoConOtroFormato(producto(1L, "PK3000", "EGSA", "SABI"));

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-3000", "100", "PACKSON")), "EGSA");

        assertThat(r.porCodigo()).doesNotContainKey("PK-3000");
        assertThat(r.conOtroFormato()).isEmpty();
    }

    /** PK3000 es SABI y PK-3000 es PACKSON: la marca de la lista decide cual es. */
    @Test
    void otroFormato_dosConLaMismaClave_laMarcaDecide() {
        Producto packson = producto(2L, "PK-3000", "EGSA", "PACKSON");
        enElCatalogoConOtroFormato(producto(1L, "PK3000", "EGSA", "SABI"), packson);

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK.3000", "100", "PACKSON")), "EGSA");

        assertThat(r.porCodigo().get("PK.3000")).containsExactly(packson);
    }

    @Test
    void otroFormato_dosDeLaMismaMarca_noSeSabeCualYNoLoToma() {
        enElCatalogoConOtroFormato(producto(1L, "PK2000", "EGSA", "PACKSON"), producto(2L, "PK.2000", "EGSA", "PACKSON"));

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON")), "EGSA");

        assertThat(r.porCodigo()).doesNotContainKey("PK-2000");
    }

    @Test
    void otroFormato_laListaYaTraeSuPropioCodigo_noLoUsaDosVeces() {
        Producto pk2000 = producto(1L, "PK2000", "EGSA", "PACKSON");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(pk2000));
        enElCatalogoConOtroFormato(pk2000);

        ProductosDeLaLista r = service.buscarProductos(
                List.of(fila("PK2000", "100", "PACKSON"), fila("PK-2000", "120", "PACKSON")), "EGSA");

        assertThat(r.porCodigo().get("PK2000")).containsExactly(pk2000);
        assertThat(r.porCodigo()).doesNotContainKey("PK-2000");
    }

    @Test
    void otroFormato_dosCodigosDeLaListaConLaMismaClave_ningunoSeToma() {
        enElCatalogoConOtroFormato(producto(1L, "PK2000", "EGSA", "PACKSON"));

        ProductosDeLaLista r = service.buscarProductos(
                List.of(fila("PK-2000", "100", "PACKSON"), fila("PK.2000", "120", "PACKSON")), "EGSA");

        assertThat(r.conOtroFormato()).isEmpty();
    }

    @Test
    void otroFormato_deOtroProveedor_noLoToma() {
        enElCatalogoConOtroFormato(producto(1L, "PK2000", "Autopartes del Sur", "PACKSON"));

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON")), "EGSA");

        assertThat(r.conOtroFormato()).isEmpty();
    }

    @Test
    void otroFormato_elProductoNoTieneMarca_noHayComoConfirmar() {
        enElCatalogoConOtroFormato(producto(1L, "PK2000", "EGSA", null));

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON")), "EGSA");

        assertThat(r.conOtroFormato()).isEmpty();
    }

    /** La lista de ADS no trae marca: sin marca no hay como confirmar, asi que ni se busca. */
    @SuppressWarnings("unchecked")
    @Test
    void otroFormato_laListaNoDiceLaMarca_niSeIntenta() {
        service.buscarProductos(List.of(fila("PK-2000", "100", null)), "EGSA");

        verify(productoRepository, never()).findByClaveSkuIn(any(Collection.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void otroFormato_elCodigoVieneConDosMarcas_niSeIntenta() {
        service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON"), fila("PK-2000", "90", "SABI")), "EGSA");

        verify(productoRepository, never()).findByClaveSkuIn(any(Collection.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void otroFormato_sinProveedor_niSeIntenta() {
        service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON"), fila(null, "1", "X"), fila(" ", "1", "X")), null);

        verify(productoRepository, never()).findByClaveSkuIn(any(Collection.class));
    }

    @Test
    void otroFormato_conUnoDelProveedorConEseCodigo_noBuscaMas() {
        Producto propio = producto(1L, "PK-2000", "EGSA", "PACKSON");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(propio));

        ProductosDeLaLista r = service.buscarProductos(List.of(fila("PK-2000", "100", "PACKSON")), "EGSA");

        assertThat(r.porCodigo().get("PK-2000")).containsExactly(propio);
        assertThat(r.conOtroFormato()).isEmpty();
    }

    // --- importacion manual: las mismas reglas ---

    /** La lista de ADS del 13/10 le puso su precio a 29 productos de EGSA por esto. */
    @Test
    void preview_elCodigoSoloExisteEnOtroProveedor_esNoEncontrado() {
        configurar("Autopartes del Sur");
        String id = subir("sku,precio\n233215,29362.06\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "233215", "EGSA", "TARANTO")));

        PrecioImportPreviewResponse r = service.preview(id, "sku", "precio", "Autopartes del Sur");

        assertThat(r.ok()).isZero();
        assertThat(r.noEncontrados()).isEqualTo(1);
    }

    /** EGSA usa el 2144 para un arbol de levas BH y un tornillo de REY GOMA. */
    @Test
    void aplicar_codigoRepetidoConDosMarcas_escribeUnaVezElPrecioDeSuMarca() {
        configurar("EGSA");
        batchConId();
        String id = subir("sku,precio,marca\n2144,155275.52,BH\n2144,579.44,REY GOMA\n");
        Producto arbol = producto(1L, "2144", "EGSA", "BH");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(arbol));

        aplicar(id, "EGSA");

        assertThat(arbol.getPrecioCosto()).isEqualByComparingTo("155275.52");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.partvision.pricing.domain.HistorialPrecio>> historial = ArgumentCaptor.forClass(List.class);
        verify(historialRepo).saveAll(historial.capture());
        assertThat(historial.getValue()).hasSize(1);
        assertThat(service.getProgresoImport().ultimoResultado().aplicados()).isEqualTo(1);
    }

    @Test
    void preview_codigoRepetidoConDosMarcas_muestraElPrecioDeSuMarca() {
        configurar("EGSA");
        String id = subir("sku,precio,marca\n2144,579.44,REY GOMA\n2144,155275.52,BH\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "2144", "EGSA", "BH")));

        PrecioImportPreviewResponse r = service.preview(id, "sku", "precio", "EGSA");

        assertThat(r.total()).isEqualTo(1);
        assertThat(r.ok()).isEqualTo(1);
        assertThat(r.filas().get(0).precioCostoCsv()).isEqualByComparingTo("155275.52");
        assertThat(r.filas().get(0).fila()).isEqualTo(2);
    }

    @Test
    void aplicar_codigoRepetidoMismaMarcaYDistintoPrecio_esConflictoYNoSeToca() {
        configurar("EGSA");
        batchConId();
        String id = subir("sku,precio,marca\nA1,100,BH\nA1,120,BH\n");
        Producto p = producto(1L, "A1", "EGSA", "BH");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(p));

        aplicar(id, "EGSA");

        assertThat(p.getPrecioCosto()).isNull();
        assertThat(service.getProgresoImport().ultimoResultado().conflictos()).isEqualTo(1);
    }

    @Test
    void preview_codigoRepetidoSinSaberCual_explicaElConflicto() {
        configurar("EGSA");
        String id = subir("sku,precio,marca\nA1,100,BH\nA1,120,BH\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "A1", "EGSA", "BH")));

        PrecioImportPreviewResponse r = service.preview(id, "sku", "precio", "EGSA");

        assertThat(r.conflictos()).isEqualTo(1);
        assertThat(r.filas().get(0).productoDescripcion()).contains("repetido");
    }

    /** La lista de EGSA trae ~2.700 filas en $0: la importacion vieja las cargaba como precio. */
    @Test
    void aplicar_precioEnCeroOdisparatado_noSeAplica() {
        configurar("EGSA");
        batchConId();
        String id = subir("sku,precio\nA1,0\nA2,-5\nA3,1000000000\n");

        aplicar(id, "EGSA");

        assertThat(service.getProgresoImport().ultimoResultado().aplicados()).isZero();
        assertThat(service.getProgresoImport().ultimoResultado().total()).isZero();
    }

    @Test
    void aplicar_codigoConOtroFormato_actualizaElProductoQueYaExiste() {
        configurar("EGSA");
        batchConId();
        String id = subir("sku,precio,marca\nPK-2000,500,PACKSON\n");
        Producto pk2000 = producto(1L, "PK2000", "EGSA", "PACKSON");
        enElCatalogoConOtroFormato(pk2000);

        aplicar(id, "EGSA");

        assertThat(pk2000.getPrecioCosto()).isEqualByComparingTo("500.00");
    }

    /** El mismo repuesto con otro costo, como los ~10.300 codigos que ya estan en los dos. */
    @Test
    void crearFaltantes_elCodigoSoloExisteEnOtroProveedor_loDaDeAltaParaEste() {
        String id = subir("sku,precio,marca\n801111A,391612.47,DUNCAN\n");
        when(productoRepository.findBySkuIn(any())).thenReturn(List.of(producto(1L, "801111A", "Autopartes del Sur", null)));

        service.crearFaltantes(id, "sku", "precio", "EGSA", Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> aCrear = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(aCrear.capture(), any());
        assertThat(aCrear.getValue()).singleElement()
                .satisfies(f -> assertThat(f.proveedor()).isEqualTo("EGSA"));
    }

    @Test
    void crearFaltantes_elCodigoLlegoConOtroFormato_noLoDuplica() {
        String id = subir("sku,precio,marca\nPK-2000,500,PACKSON\n");
        enElCatalogoConOtroFormato(producto(1L, "PK2000", "EGSA", "PACKSON"));

        service.crearFaltantes(id, "sku", "precio", "EGSA", Set.of());

        verify(bulkImporter, never()).importar(any(), any());
    }
}
