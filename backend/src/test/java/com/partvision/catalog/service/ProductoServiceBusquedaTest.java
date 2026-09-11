package com.partvision.catalog.service;

import com.partvision.ai.search.ProcessedQuery;
import com.partvision.ai.search.SearchOrchestrator;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoListItemResponse;
import com.partvision.catalog.repository.ProductoCodigoRepository;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.inventory.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El reintento con sinonimos: cuando la IA interpreto la consulta y la busqueda trajo casi
 * nada, se vuelve a intentar con los sinonimos. Solo se queda con el segundo resultado si
 * realmente trae mas.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceBusquedaTest {

    private static final Pageable PAGINA = PageRequest.of(0, 20);

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProductoCodigoRepository productoCodigoRepository;
    @Mock
    private MarcaService marcaService;
    @Mock
    private CategoriaService categoriaService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private SearchOrchestrator orchestrator;

    private ProductoService service() {
        return new ProductoService(productoRepository, productoCodigoRepository, marcaService,
                categoriaService, new ProductoMatcher(), stockRepository, orchestrator);
    }

    private static Producto producto(long id, String sku) {
        Marca marca = new Marca();
        marca.setId(1L);
        marca.setNombre("Mahle");
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Pastillas");
        p.setMarca(marca);
        p.setEstado(ProductoEstado.ACTIVO);
        return p;
    }

    private static Page<Producto> pagina(int cantidad) {
        List<Producto> items = new java.util.ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            items.add(producto(i + 1L, "SKU" + i));
        }
        return new PageImpl<>(items, PAGINA, cantidad);
    }

    @Test
    void sinInterpretacionDeIa_noReintenta() {
        when(orchestrator.processQuery("pastillas")).thenReturn(ProcessedQuery.passthrough("pastillas"));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(1));

        service().buscarPorTexto("pastillas", PAGINA);

        verify(productoRepository).buscarInteligente(any(), anyString(), any());
    }

    @Test
    void conIaPeroSinSinonimos_noReintenta() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of(), true));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(1));

        service().buscarPorTexto("pastillas", PAGINA);

        verify(productoRepository).buscarInteligente(any(), anyString(), any());
    }

    /** Con resultados de sobra no tiene sentido gastar otra consulta. */
    @Test
    void conMuchosResultados_noReintenta() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of("balatas"), true));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(10));

        service().buscarPorTexto("pastillas", PAGINA);

        verify(productoRepository).buscarInteligente(any(), anyString(), any());
    }

    /** Si el sinonimo no agrega ninguna palabra nueva, la consulta expandida seria la misma. */
    @Test
    void sinonimoQueNoAgregaTokens_noReintenta() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of("de"), true));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(1));

        service().buscarPorTexto("pastillas", PAGINA);

        verify(productoRepository, never()).buscarInteligente(any(), eq("pastillas de"), any());
    }

    @Test
    void reintentoConMasResultados_sePrefiere() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of("balatas"), true));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(1));
        when(productoRepository.buscarInteligente(any(), eq("pastillas balatas"), eq(PAGINA)))
                .thenReturn(pagina(5));
        when(stockRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Page<ProductoListItemResponse> resultado = service().buscarPorTexto("pastillas", PAGINA);

        assertThat(resultado.getTotalElements()).isEqualTo(5);
    }

    @Test
    void reintentoConMenosResultados_seDescarta() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of("balatas"), true));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(2));
        when(productoRepository.buscarInteligente(any(), eq("pastillas balatas"), eq(PAGINA)))
                .thenReturn(pagina(0));
        when(stockRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Page<ProductoListItemResponse> resultado = service().buscarPorTexto("pastillas", PAGINA);

        assertThat(resultado.getTotalElements()).isEqualTo(2);
    }

    /** Con filtro de stock el reintento tiene que usar la consulta que respeta ese filtro. */
    @Test
    void conFiltroDeStock_reintentaPorLaConsultaConStock() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(new ProcessedQuery("pastillas", List.of("balatas"), true));
        when(productoRepository.buscarInteligenteConStock(any(), eq("pastillas"), eq(true), eq(PAGINA)))
                .thenReturn(pagina(1));
        when(productoRepository.buscarInteligenteConStock(any(), eq("pastillas balatas"), eq(true), eq(PAGINA)))
                .thenReturn(pagina(4));
        when(stockRepository.findByProductoIdIn(any())).thenReturn(List.of());

        Page<ProductoListItemResponse> resultado = service().findAll("pastillas", true, PAGINA);

        assertThat(resultado.getTotalElements()).isEqualTo(4);
        verify(productoRepository).buscarInteligenteConStock(any(), eq("pastillas balatas"), anyBoolean(), any());
    }

    /** Pagina vacia: no se consulta el stock de una lista de ids vacia. */
    @Test
    void paginaVacia_noConsultaStock() {
        when(orchestrator.processQuery("pastillas"))
                .thenReturn(ProcessedQuery.passthrough("pastillas"));
        when(productoRepository.buscarInteligente(any(), eq("pastillas"), eq(PAGINA))).thenReturn(pagina(0));

        Page<ProductoListItemResponse> resultado = service().buscarPorTexto("pastillas", PAGINA);

        assertThat(resultado).isEmpty();
        verify(stockRepository, never()).findByProductoIdIn(any());
    }

    /** Una letra suelta no es un termino de busqueda: se descarta antes de consultar. */
    @Test
    void tokensDeUnaLetra_seDescartan() {
        when(orchestrator.processQuery("a pastillas")).thenReturn(ProcessedQuery.passthrough("a pastillas"));
        when(productoRepository.buscarInteligente(any(), eq("a pastillas"), eq(PAGINA))).thenReturn(pagina(0));

        service().buscarPorTexto("a pastillas", PAGINA);

        verify(productoRepository).buscarInteligente(List.of("pastillas"), "a pastillas", PAGINA);
    }

    /** Dos productos igual de buenos no son un match: decide una persona, no el sistema. */
    @Test
    void matchNormalizado_dosCandidatosFuertes_noDevuelveNinguno() {
        when(productoRepository.buscarPorSkuPrefijo(any(), any()))
                .thenReturn(List.of(producto(1L, "ABC-1"), producto(2L, "ABC-1")));

        assertThat(service().matchNormalizado("ABC-1", null)).isEmpty();
    }

    @Test
    void matchNormalizado_codigoSinAncla_noConsulta() {
        assertThat(service().matchNormalizado("--", null)).isEmpty();
        verify(productoRepository, never()).buscarPorSkuPrefijo(any(), any());
    }

    @Test
    void consultaNula_listaTodo() {
        when(orchestrator.processQuery(null)).thenReturn(ProcessedQuery.passthrough(null));
        when(productoRepository.findAllBy(PAGINA)).thenReturn(pagina(0));

        assertThat(service().buscarPorTexto(null, PAGINA)).isEmpty();
    }

    @Test
    void consultaEnBlanco_listaTodo() {
        when(orchestrator.processQuery("   ")).thenReturn(ProcessedQuery.passthrough("   "));
        when(productoRepository.findAllBy(PAGINA)).thenReturn(pagina(0));

        assertThat(service().buscarPorTexto("   ", PAGINA)).isEmpty();
    }
}
