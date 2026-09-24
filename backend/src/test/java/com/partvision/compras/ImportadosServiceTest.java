package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.AltaImportadoRequest;
import com.partvision.compras.dto.ImportadoPendienteResponse;
import com.partvision.compras.dto.ImportadoResueltoResponse;
import com.partvision.compras.dto.VincularImportadoRequest;
import com.partvision.compras.repository.CompraLineaRepository;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.service.StockService;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las piezas que llegan sin codigo (pedidos puntuales) y que el cliente decide sumar al
 * catalogo porque cree que las va a volver a pedir.
 */
@ExtendWith(MockitoExtension.class)
class ImportadosServiceTest {

    @Mock private CompraLineaRepository lineaRepo;
    @Mock private ProductoRepository productoRepo;
    @Mock private ProductoService productoService;
    @Mock private StockService stockService;
    @Mock private UbicacionService ubicacionService;

    private ImportadosService service;

    @BeforeEach
    void setUp() {
        service = new ImportadosService(lineaRepo, productoRepo, productoService, stockService, ubicacionService);
        lenient().when(lineaRepo.save(any(CompraLinea.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CompraLinea importado(CompraEstado estadoCompra) {
        Compra compra = new Compra();
        compra.setId(3L);
        compra.setNumeroFactura("900000482");
        compra.setFechaFactura(LocalDate.of(2026, 8, 25));
        compra.setProveedor("EGSA");
        compra.setEstado(estadoCompra);
        CompraLinea linea = new CompraLinea();
        linea.setId(40L);
        linea.setCodigo("IMPORTADOS");
        linea.setDescripcion("bie0381 biela om651");
        linea.setCantidad(2);
        compra.addLinea(linea);
        return linea;
    }

    private static Producto producto(Long id, String sku) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("BIELA OM651");
        p.setEstado(ProductoEstado.ACTIVO);
        return p;
    }

    private static Ubicacion ubicacion() {
        Ubicacion u = new Ubicacion();
        u.setId(50L);
        u.setCodigo("A-01");
        return u;
    }

    private void productoSeCrea(Long id, String sku) {
        ProductoResponse creado = new ProductoResponse(id, sku, null, null, null, null, "BIELA OM651",
                ProductoEstado.ACTIVO, Map.of(), List.of(), "EGSA", null, null, null, null);
        when(productoService.create(any(ProductoRequest.class))).thenReturn(creado);
        when(productoService.getEntity(id)).thenReturn(producto(id, sku));
    }

    // --- listado ---

    @Test
    void listarPendientes_traeLasLineasConSuFactura() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);
        when(lineaRepo.findSinProductoPorCodigo(eq("IMPORTADOS"), any()))
                .thenReturn(new PageImpl<>(List.of(linea)));

        Page<ImportadoPendienteResponse> pagina = service.listarPendientes(PageRequest.of(0, 20));

        ImportadoPendienteResponse r = pagina.getContent().get(0);
        assertThat(r.lineaId()).isEqualTo(40L);
        assertThat(r.factura()).isEqualTo("900000482");
        assertThat(r.estadoCompra()).isEqualTo("EN_TRANSITO");
        assertThat(r.descripcion()).isEqualTo("bie0381 biela om651");
        assertThat(r.cantidad()).isEqualTo(2);
        assertThat(r.sugerencia()).isNull();   // ese codigo no esta en el catalogo
    }

    // --- sugerencia: el codigo que la planilla escribe dentro de la descripcion ---

    @Test
    void codigoEnLaDescripcion_loSacaDeLaPrimeraPalabra() {
        assertThat(ImportadosService.codigoEnLaDescripcion("bie0199 biela")).isEqualTo("BIE0199");
        assertThat(ImportadosService.codigoEnLaDescripcion("  jt1232 junta tapa om926 "))
                .isEqualTo("JT1232");
        assertThat(ImportadosService.codigoEnLaDescripcion("KDRB4D*C*CH kit distr kwin"))
                .isEqualTo("KDRB4D*C*CH");
    }

    /** Una descripcion normal no es un codigo: asociar por la primera palabra seria un desastre. */
    @Test
    void codigoEnLaDescripcion_descartaLoQueNoParezcaUno() {
        assertThat(ImportadosService.codigoEnLaDescripcion("termotato perkins")).isNull();  // sin digitos
        assertThat(ImportadosService.codigoEnLaDescripcion("6913 reten")).isNull();         // sin letras
        assertThat(ImportadosService.codigoEnLaDescripcion("ab1 cosa")).isNull();           // muy corta
        assertThat(ImportadosService.codigoEnLaDescripcion("   ")).isNull();
        assertThat(ImportadosService.codigoEnLaDescripcion(null)).isNull();
    }

    /**
     * Con ~10.300 SKU repetidos uno por proveedor, el mismo codigo devuelve dos productos.
     * Gana el del proveedor de la factura: el otro cargaria el stock en el producto equivocado.
     */
    @Test
    void sugerencia_prefiereElProductoDelProveedorDeLaFactura() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);   // factura de EGSA
        when(lineaRepo.findSinProductoPorCodigo(eq("IMPORTADOS"), any()))
                .thenReturn(new PageImpl<>(List.of(linea)));
        when(productoRepo.findBySkuIn(Set.of("BIE0381"))).thenReturn(List.of(
                proveedorDe(producto(900L, "BIE0381"), "Autopartes del Sur"),
                proveedorDe(producto(901L, "BIE0381"), "EGSA")));

        var sugerencia = service.listarPendientes(PageRequest.of(0, 20))
                .getContent().get(0).sugerencia();

        assertThat(sugerencia).isNotNull();
        assertThat(sugerencia.productoId()).isEqualTo(901L);
        assertThat(sugerencia.sku()).isEqualTo("BIE0381");
        assertThat(sugerencia.mismoProveedor()).isTrue();
    }

    /** Si el unico candidato es de otro proveedor se muestra igual, pero avisando. */
    @Test
    void sugerencia_deOtroProveedor_seMarca() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);   // factura de EGSA
        when(lineaRepo.findSinProductoPorCodigo(eq("IMPORTADOS"), any()))
                .thenReturn(new PageImpl<>(List.of(linea)));
        when(productoRepo.findBySkuIn(Set.of("BIE0381"))).thenReturn(List.of(
                proveedorDe(producto(900L, "BIE0381"), "Autopartes del Sur")));

        var sugerencia = service.listarPendientes(PageRequest.of(0, 20))
                .getContent().get(0).sugerencia();

        assertThat(sugerencia.productoId()).isEqualTo(900L);
        assertThat(sugerencia.proveedor()).isEqualTo("Autopartes del Sur");
        assertThat(sugerencia.mismoProveedor()).isFalse();
    }

    /** Sin codigo reconocible no se consulta el catalogo: son la mayoria de las lineas. */
    @Test
    void sugerencia_sinCodigoEnLaDescripcion_niConsultaElCatalogo() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);
        linea.setDescripcion("termotato perkins");
        when(lineaRepo.findSinProductoPorCodigo(eq("IMPORTADOS"), any()))
                .thenReturn(new PageImpl<>(List.of(linea)));

        assertThat(service.listarPendientes(PageRequest.of(0, 20))
                .getContent().get(0).sugerencia()).isNull();
        verify(productoRepo, never()).findBySkuIn(any());
    }

    private static Producto proveedorDe(Producto p, String proveedor) {
        p.setProveedor(proveedor);
        return p;
    }

    // --- SKU propuesto ---

    @Test
    void proponerSku_elPrimeroEsImp00001() {
        when(productoRepo.findSkusConPrefijo("IMP-")).thenReturn(List.of());

        assertThat(service.proponerSku()).isEqualTo("IMP-00001");
    }

    /** Sigue al mayor, e ignora lo que no tenga numero despues del prefijo. */
    @Test
    void proponerSku_sigueAlMayorExistente() {
        when(productoRepo.findSkusConPrefijo("IMP-"))
                .thenReturn(List.of("IMP-00007", "imp-00012", "IMP-ESPECIAL", "IMP-"));

        assertThat(service.proponerSku()).isEqualTo("IMP-00013");
    }

    /** Si justo lo tomo otra persona, pasa al siguiente. */
    @Test
    void proponerSku_siElNumeroYaEstaTomado_pasaAlSiguiente() {
        when(productoRepo.findSkusConPrefijo("IMP-")).thenReturn(List.of());
        when(productoRepo.existsBySkuIgnoreCase("IMP-00001")).thenReturn(true);

        assertThat(service.proponerSku()).isEqualTo("IMP-00002");
    }

    // --- dar de alta ---

    @Test
    void darDeAlta_enCompraSinIngresar_creaYAsociaSinCargarStock() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));
        productoSeCrea(900L, "IMP-00001");

        ImportadoResueltoResponse r = service.darDeAlta(40L,
                new AltaImportadoRequest(" imp-00001 ", " BIELA OM651 ", null));

        assertThat(r.sku()).isEqualTo("IMP-00001");
        assertThat(r.stockCargado()).isFalse();
        assertThat(r.mensaje()).contains("cuando se ingrese la compra");
        assertThat(linea.getProducto().getId()).isEqualTo(900L);
        verify(stockService, never()).registrarEntrada(any());

        ArgumentCaptor<ProductoRequest> alta = ArgumentCaptor.forClass(ProductoRequest.class);
        verify(productoService).create(alta.capture());
        assertThat(alta.getValue().sku()).isEqualTo("IMP-00001");
        assertThat(alta.getValue().descripcion()).isEqualTo("BIELA OM651");
        assertThat(alta.getValue().proveedor()).isEqualTo("EGSA");
    }

    /** La compra ya ingreso sin esta linea: su stock se carga ahora, en la ubicacion elegida. */
    @Test
    void darDeAlta_enCompraYaIngresada_cargaElStockEnElMomento() {
        CompraLinea linea = importado(CompraEstado.INGRESADA);
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));
        when(ubicacionService.getEntity(50L)).thenReturn(ubicacion());
        productoSeCrea(900L, "IMP-00001");

        ImportadoResueltoResponse r = service.darDeAlta(40L,
                new AltaImportadoRequest("IMP-00001", "BIELA OM651", 50L));

        assertThat(r.stockCargado()).isTrue();
        assertThat(r.ubicacionCodigo()).isEqualTo("A-01");
        assertThat(linea.getUbicacionIngreso().getId()).isEqualTo(50L);

        ArgumentCaptor<EntradaRequest> entrada = ArgumentCaptor.forClass(EntradaRequest.class);
        verify(stockService).registrarEntrada(entrada.capture());
        assertThat(entrada.getValue().productoId()).isEqualTo(900L);
        assertThat(entrada.getValue().ubicacionId()).isEqualTo(50L);
        assertThat(entrada.getValue().cantidad()).isEqualTo(2);
        assertThat(entrada.getValue().motivo()).contains("900000482");
    }

    @Test
    void darDeAlta_compraYaIngresadaSinUbicacion_seRechaza() {
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(importado(CompraEstado.INGRESADA)));

        assertThatThrownBy(() -> service.darDeAlta(40L, new AltaImportadoRequest("IMP-00001", "BIELA", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ubicacion");
        verify(productoService, never()).create(any());
    }

    /** El SKU no puede chocar con ninguno del catalogo, de ninguna marca ni proveedor. */
    @Test
    void darDeAlta_skuQueYaExiste_seRechazaYSugiereOtro() {
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(importado(CompraEstado.EN_TRANSITO)));
        when(productoRepo.existsBySkuIgnoreCase(anyString()))
                .thenAnswer(inv -> "272005".equals(inv.getArgument(0)));
        when(productoRepo.findSkusConPrefijo("IMP-")).thenReturn(List.of("IMP-00004"));

        assertThatThrownBy(() -> service.darDeAlta(40L, new AltaImportadoRequest("272005", "BIELA", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("272005")
                .hasMessageContaining("IMP-00005");
        verify(productoService, never()).create(any());
    }

    // --- vincular ---

    /** La misma pieza pedida otra vez: se asocia al producto que ya estaba, no se duplica. */
    @Test
    void vincular_aUnProductoExistente_noCreaNada() {
        CompraLinea linea = importado(CompraEstado.POR_UBICAR);
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));
        when(productoService.getEntity(900L)).thenReturn(producto(900L, "IMP-00001"));

        ImportadoResueltoResponse r = service.vincular(40L, new VincularImportadoRequest(900L, null));

        assertThat(r.productoId()).isEqualTo(900L);
        assertThat(r.mensaje()).startsWith("Asociado a IMP-00001");
        assertThat(linea.getProducto().getId()).isEqualTo(900L);
        verify(productoService, never()).create(any());
        verify(stockService, never()).registrarEntrada(any());
    }

    @Test
    void vincular_enCompraYaIngresada_cargaElStock() {
        CompraLinea linea = importado(CompraEstado.INGRESADA);
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));
        when(productoService.getEntity(900L)).thenReturn(producto(900L, "IMP-00001"));
        when(ubicacionService.getEntity(50L)).thenReturn(ubicacion());

        ImportadoResueltoResponse r = service.vincular(40L, new VincularImportadoRequest(900L, 50L));

        assertThat(r.stockCargado()).isTrue();
        verify(stockService).registrarEntrada(any());
    }

    @Test
    void vincular_compraYaIngresadaSinUbicacion_seRechaza() {
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(importado(CompraEstado.INGRESADA)));

        assertThatThrownBy(() -> service.vincular(40L, new VincularImportadoRequest(900L, null)))
                .isInstanceOf(BusinessException.class);
    }

    // --- lineas que no se pueden resolver ---

    @Test
    void lineaInexistente_es404() {
        when(lineaRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vincular(99L, new VincularImportadoRequest(900L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lineaConCodigoReal_noEsUnImportado() {
        CompraLinea linea = importado(CompraEstado.EN_TRANSITO);
        linea.setCodigo("272005");
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));

        assertThatThrownBy(() -> service.vincular(40L, new VincularImportadoRequest(900L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no es un importado");
    }

    /** Resolverla dos veces cargaria el stock dos veces. */
    @Test
    void lineaYaAsociada_noSeResuelveDeNuevo() {
        CompraLinea linea = importado(CompraEstado.INGRESADA);
        linea.setProducto(producto(900L, "IMP-00001"));
        when(lineaRepo.findById(40L)).thenReturn(Optional.of(linea));

        assertThatThrownBy(() -> service.vincular(40L, new VincularImportadoRequest(901L, 50L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya esta asociada");
        verify(stockService, never()).registrarEntrada(any());
    }
}
