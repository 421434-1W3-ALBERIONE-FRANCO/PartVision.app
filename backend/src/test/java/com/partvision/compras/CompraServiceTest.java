package com.partvision.compras;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.*;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.repository.StockRepository;
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
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock private CompraRepository compraRepo;
    @Mock private ProductoRepository productoRepo;
    @Mock private StockService stockService;
    @Mock private StockRepository stockRepository;
    @Mock private UbicacionService ubicacionService;

    private CompraService service;

    @BeforeEach
    void setUp() {
        service = new CompraService(compraRepo, productoRepo, stockService, stockRepository, ubicacionService);
    }

    // --- registrarRecepcion ---

    @Test
    void registrarRecepcion_nuevaFactura_creaCompra() {
        var lineas = List.of(new RecepcionLineaRequest("SKU1", "Filtro aceite", 2));
        var request = new RecepcionCompraRequest("FAC-001", "15/06/2025", "Proveedor A", "EN_TRANSITO", lineas);

        Producto producto = buildProducto(1L, "SKU1");
        when(compraRepo.findByNumeroFactura("FAC-001")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(producto));
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(10L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);

        assertThat(resp.numeroFactura()).isEqualTo("FAC-001");
        assertThat(resp.estado()).isEqualTo("EN_TRANSITO");
        assertThat(resp.totalLineas()).isEqualTo(1);
        assertThat(resp.lineasMatcheadas()).isEqualTo(1);
        verify(compraRepo).save(any(Compra.class));
    }

    @Test
    void registrarRecepcion_facturaExistente_retornaExistente() {
        Compra existente = new Compra();
        existente.setId(5L);
        existente.setNumeroFactura("FAC-DUP");
        existente.setFechaFactura(LocalDate.of(2025, 1, 1));
        existente.setProveedor("X");
        existente.setEstado(CompraEstado.EN_TRANSITO);
        existente.setCreatedAt(Instant.now());

        when(compraRepo.findByNumeroFactura("FAC-DUP")).thenReturn(Optional.of(existente));

        var request = new RecepcionCompraRequest("FAC-DUP", "01/01/2025", "X", "EN_TRANSITO",
                List.of(new RecepcionLineaRequest("A", "desc", 1)));

        CompraResponse resp = service.registrarRecepcion(request);

        assertThat(resp.id()).isEqualTo(5L);
        verify(compraRepo, never()).save(any());
    }

    @Test
    void registrarRecepcion_codigoBlanco_usaIMPORTADOS() {
        var lineas = List.of(
                new RecepcionLineaRequest(null, "Sin codigo", 3),
                new RecepcionLineaRequest("  ", "Espacio", 1)
        );
        var request = new RecepcionCompraRequest("FAC-IMP", "01/01/2025", "Prov", "EN_TRANSITO", lineas);

        when(compraRepo.findByNumeroFactura("FAC-IMP")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(11L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);

        assertThat(resp.totalLineas()).isEqualTo(2);
        ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);
        verify(compraRepo).save(captor.capture());
        Compra saved = captor.getValue();
        assertThat(saved.getLineas()).allSatisfy(l -> assertThat(l.getCodigo()).isEqualTo("IMPORTADOS"));
    }

    @Test
    void registrarRecepcion_fechaISO_parsed() {
        var request = new RecepcionCompraRequest("FAC-ISO", "2025-06-15", "Prov", "EN_TRANSITO",
                List.of(new RecepcionLineaRequest("X", "desc", 1)));

        when(compraRepo.findByNumeroFactura("FAC-ISO")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(12L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);
        assertThat(resp.fechaFactura()).isEqualTo(LocalDate.of(2025, 6, 15));
    }

    @Test
    void registrarRecepcion_fechaInvalida_lanzaBusinessException() {
        var request = new RecepcionCompraRequest("FAC-BAD", "not-a-date", "Prov", "EN_TRANSITO",
                List.of(new RecepcionLineaRequest("X", "desc", 1)));

        when(compraRepo.findByNumeroFactura("FAC-BAD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarRecepcion(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Formato de fecha inválido");
    }

    @Test
    void registrarRecepcion_estatusNull_defaultEnTransito() {
        var request = new RecepcionCompraRequest("FAC-NULL", "01/01/2025", "Prov", null,
                List.of(new RecepcionLineaRequest("X", "desc", 1)));

        when(compraRepo.findByNumeroFactura("FAC-NULL")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(13L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);
        assertThat(resp.estado()).isEqualTo("EN_TRANSITO");
    }

    @Test
    void registrarRecepcion_estatusIngresada_parsedCorrectly() {
        var request = new RecepcionCompraRequest("FAC-ING", "01/01/2025", "Prov", "INGRESADA en bodega",
                List.of(new RecepcionLineaRequest("X", "desc", 1)));

        when(compraRepo.findByNumeroFactura("FAC-ING")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(14L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);
        assertThat(resp.estado()).isEqualTo("INGRESADA");
    }

    @Test
    void registrarRecepcion_sinMatch_lineaSinProducto() {
        var request = new RecepcionCompraRequest("FAC-NOMATCH", "01/01/2025", "Prov", "EN_TRANSITO",
                List.of(new RecepcionLineaRequest("UNKNOWN", "desc", 5)));

        when(compraRepo.findByNumeroFactura("FAC-NOMATCH")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());
        when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            c.setId(15L);
            c.setCreatedAt(Instant.now());
            return c;
        });

        CompraResponse resp = service.registrarRecepcion(request);
        assertThat(resp.lineasMatcheadas()).isEqualTo(0);
    }

    // --- marcarIngresada ---

    @Test
    void marcarIngresada_cargaStockPorLinea() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        CompraLinea linea = compra.getLineas().getFirst();
        linea.setId(100L);
        Producto producto = buildProducto(1L, "SKU1");
        linea.setProducto(producto);

        Ubicacion ub = buildUbicacion(50L, "A-1-1");

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ub);

        var asignacion = new CambiarEstadoRequest.LineaUbicacion(100L, 50L);
        var request = new CambiarEstadoRequest(List.of(asignacion));
        when(compraRepo.save(any(Compra.class))).thenReturn(compra);

        CompraResponse resp = service.marcarIngresada(1L, request);

        assertThat(resp.estado()).isEqualTo("INGRESADA");
        verify(stockService).registrarEntrada(any(EntradaRequest.class));
    }

    @Test
    void marcarIngresada_sinProducto_noCargarStock() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        CompraLinea linea = compra.getLineas().getFirst();
        linea.setId(100L);
        linea.setProducto(null);

        Ubicacion ub = buildUbicacion(50L, "A-1-1");

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ub);
        when(compraRepo.save(any(Compra.class))).thenReturn(compra);

        var request = new CambiarEstadoRequest(List.of(new CambiarEstadoRequest.LineaUbicacion(100L, 50L)));

        service.marcarIngresada(1L, request);

        verify(stockService, never()).registrarEntrada(any());
    }

    @Test
    void marcarIngresada_yaIngresada_lanzaExcepcion() {
        Compra compra = buildCompraConLineas(CompraEstado.INGRESADA);

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));

        var request = new CambiarEstadoRequest(List.of(new CambiarEstadoRequest.LineaUbicacion(100L, 50L)));

        assertThatThrownBy(() -> service.marcarIngresada(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya fue marcada como ingresada");
    }

    @Test
    void marcarIngresada_compraNoExiste_lanzaExcepcion() {
        when(compraRepo.findWithLineasById(999L)).thenReturn(Optional.empty());

        var request = new CambiarEstadoRequest(List.of(new CambiarEstadoRequest.LineaUbicacion(1L, 1L)));

        assertThatThrownBy(() -> service.marcarIngresada(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Compra no encontrada");
    }

    @Test
    void marcarIngresada_lineaSinAsignacion_skip() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        CompraLinea linea = compra.getLineas().getFirst();
        linea.setId(100L);
        linea.setProducto(buildProducto(1L, "SKU1"));

        Ubicacion ub = buildUbicacion(50L, "A-1-1");

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ub);
        when(compraRepo.save(any(Compra.class))).thenReturn(compra);

        var request = new CambiarEstadoRequest(List.of(new CambiarEstadoRequest.LineaUbicacion(999L, 50L)));

        service.marcarIngresada(1L, request);

        verify(stockService, never()).registrarEntrada(any());
    }

    // --- listar ---

    @Test
    void listar_conEstado_filtra() {
        Compra c = buildCompraSimple();
        Pageable pageable = PageRequest.of(0, 10);
        when(compraRepo.findByEstadoOrderByCreatedAtDesc(CompraEstado.EN_TRANSITO, pageable))
                .thenReturn(new PageImpl<>(List.of(c)));

        Page<CompraResponse> page = service.listar(CompraEstado.EN_TRANSITO, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(compraRepo).findByEstadoOrderByCreatedAtDesc(eq(CompraEstado.EN_TRANSITO), eq(pageable));
    }

    @Test
    void listar_sinEstado_retornaTodas() {
        Compra c = buildCompraSimple();
        Pageable pageable = PageRequest.of(0, 10);
        when(compraRepo.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(c)));

        Page<CompraResponse> page = service.listar(null, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(compraRepo).findAllByOrderByCreatedAtDesc(pageable);
    }

    // --- detalle ---

    @Test
    void detalle_conSugerencias_retornaUbicacion() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        CompraLinea linea = compra.getLineas().getFirst();
        linea.setId(100L);
        Producto producto = buildProducto(1L, "SKU1");
        linea.setProducto(producto);

        Ubicacion ub = buildUbicacion(50L, "A-1-1");
        Stock stock = Stock.builder()
                .id(1L).producto(producto).ubicacion(ub).cantidad(10).build();

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));
        when(stockRepository.findByProductoIdIn(List.of(1L))).thenReturn(List.of(stock));

        CompraResponse resp = service.detalle(1L);

        assertThat(resp.lineas()).hasSize(1);
        assertThat(resp.lineas().getFirst().ubicacionSugeridaId()).isEqualTo(50L);
        assertThat(resp.lineas().getFirst().ubicacionSugeridaCodigo()).isEqualTo("A-1-1");
    }

    @Test
    void detalle_sinProductos_sinSugerencias() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        compra.getLineas().getFirst().setProducto(null);

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));

        CompraResponse resp = service.detalle(1L);

        assertThat(resp.lineas()).hasSize(1);
        assertThat(resp.lineas().getFirst().ubicacionSugeridaId()).isNull();
        verify(stockRepository, never()).findByProductoIdIn(any());
    }

    @Test
    void detalle_noExiste_lanzaExcepcion() {
        when(compraRepo.findWithLineasById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalle(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Compra no encontrada");
    }

    @Test
    void detalle_stockCero_noSugiere() {
        Compra compra = buildCompraConLineas(CompraEstado.EN_TRANSITO);
        CompraLinea linea = compra.getLineas().getFirst();
        linea.setId(100L);
        Producto producto = buildProducto(1L, "SKU1");
        linea.setProducto(producto);

        Ubicacion ub = buildUbicacion(50L, "A-1-1");
        Stock stock = Stock.builder()
                .id(1L).producto(producto).ubicacion(ub).cantidad(0).build();

        when(compraRepo.findWithLineasById(1L)).thenReturn(Optional.of(compra));
        when(stockRepository.findByProductoIdIn(List.of(1L))).thenReturn(List.of(stock));

        CompraResponse resp = service.detalle(1L);

        assertThat(resp.lineas().getFirst().ubicacionSugeridaId()).isNull();
    }

    // --- helpers ---

    private Producto buildProducto(Long id, String sku) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        return p;
    }

    private Ubicacion buildUbicacion(Long id, String codigo) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setCodigo(codigo);
        u.setPath(codigo);
        u.setActivo(true);
        return u;
    }

    private Compra buildCompraSimple() {
        Compra c = new Compra();
        c.setId(1L);
        c.setNumeroFactura("FAC-SIMPLE");
        c.setFechaFactura(LocalDate.of(2025, 1, 1));
        c.setProveedor("Prov");
        c.setEstado(CompraEstado.EN_TRANSITO);
        c.setCreatedAt(Instant.now());
        return c;
    }

    private Compra buildCompraConLineas(CompraEstado estado) {
        Compra c = buildCompraSimple();
        c.setEstado(estado);

        CompraLinea linea = new CompraLinea();
        linea.setCodigo("SKU1");
        linea.setDescripcion("Filtro");
        linea.setCantidad(5);
        c.addLinea(linea);

        return c;
    }
}
