package com.partvision.catalog.service;

import com.partvision.ai.search.AiSearchProperties;
import com.partvision.ai.search.SearchInterpreter;
import com.partvision.ai.search.SearchInterpretationCache;
import com.partvision.ai.search.SearchOrchestrator;
import com.partvision.ai.search.SearchQueryClassifier;
import org.springframework.beans.factory.ObjectProvider;
import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoCodigoRequest;
import com.partvision.catalog.dto.ProductoListItemResponse;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.repository.ProductoCodigoRepository;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

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

    @SuppressWarnings("unchecked")
    private ProductoService service() {
        var disabledProps = new AiSearchProperties(false, "gemini", "gemini-flash-latest", 1000, 0.70, 720);
        ObjectProvider<SearchInterpreter> ip = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<SearchInterpretationCache> cp = org.mockito.Mockito.mock(ObjectProvider.class);
        var orchestrator = new SearchOrchestrator(disabledProps, new SearchQueryClassifier(), ip, cp);
        return new ProductoService(productoRepository, productoCodigoRepository, marcaService, categoriaService,
                new ProductoMatcher(), stockRepository, orchestrator);
    }

    @Test
    void create_minimo_usaEstadoActivoYSinCodigos() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductoResponse response = service().create(
                new ProductoRequest(null, null, null, null, "Filtro generico", null, null, null, null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.estado()).isEqualTo(ProductoEstado.ACTIVO);
        assertThat(response.marcaId()).isNull();
        assertThat(response.detallesExtra()).isEmpty();
        assertThat(response.codigos()).isEmpty();
    }

    @Test
    void create_completo_mapeaTodo() {
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        Categoria categoria = Categoria.builder().id(7L).nombre("Filtros").build();
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(categoriaService.getEntity(7L)).thenReturn(categoria);
        when(productoRepository.existsByMarcaAndSku(marca, "ABC-123")).thenReturn(false);
        when(productoCodigoRepository.existsByCodigo("7791234567890")).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProductoResponse response = service().create(new ProductoRequest(
                "ABC-123", 5L, null, 7L, "Filtro de aceite", ProductoEstado.BORRADOR,
                Map.of("origen", "Argentina"),
                List.of(new ProductoCodigoRequest("7791234567890", "EAN13")), "Autopartes SA"));

        assertThat(response.marcaId()).isEqualTo(5L);
        assertThat(response.marcaNombre()).isEqualTo("Bosch");
        assertThat(response.categoriaId()).isEqualTo(7L);
        assertThat(response.estado()).isEqualTo(ProductoEstado.BORRADOR);
        assertThat(response.detallesExtra()).containsEntry("origen", "Argentina");
        assertThat(response.codigos()).extracting(c -> c.codigo()).containsExactly("7791234567890");
        assertThat(response.proveedor()).isEqualTo("Autopartes SA");
    }

    @Test
    void update_modificaCamposDeCatalogo() {
        Producto existente = Producto.builder().id(4L).descripcion("viejo").estado(ProductoEstado.ACTIVO).build();
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(productoRepository.existsByMarcaAndSkuAndIdNot(marca, "SKU-9", 4L)).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse r = service().update(4L, new ProductoRequest(
                "SKU-9", 5L, null, null, "nuevo", null, null, null, "Prov"));

        assertThat(r.descripcion()).isEqualTo("nuevo");
        assertThat(r.sku()).isEqualTo("SKU-9");
        assertThat(r.marcaNombre()).isEqualTo("Bosch");
        assertThat(r.proveedor()).isEqualTo("Prov");
    }

    @Test
    void update_skuDuplicadoEnOtroProducto_lanza409() {
        Producto existente = Producto.builder().id(4L).descripcion("x").estado(ProductoEstado.ACTIVO).build();
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(productoRepository.existsByMarcaAndSkuAndIdNot(marca, "DUP", 4L)).thenReturn(true);

        assertThatThrownBy(() -> service().update(4L, new ProductoRequest(
                "DUP", 5L, null, null, "x", null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_skuDuplicadoParaMarca_lanza409() {
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(productoRepository.existsByMarcaAndSku(marca, "ABC-123")).thenReturn(true);

        assertThatThrownBy(() -> service().create(
                new ProductoRequest("ABC-123", 5L, null, null, "Filtro", null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_marcaPorNombre_resuelveOCrea() {
        Marca marca = Marca.builder().id(8L).nombre("Fram").build();
        when(marcaService.getOrCreateByNombre("Fram")).thenReturn(marca);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(3L);
            return p;
        });

        ProductoResponse response = service().create(new ProductoRequest(
                null, null, "Fram", null, "Filtro detectado por IA", null, null, null, null));

        assertThat(response.marcaId()).isEqualTo(8L);
        assertThat(response.marcaNombre()).isEqualTo("Fram");
    }

    @Test
    void create_marcaIdTienePrioridadSobreNombre() {
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(4L);
            return p;
        });

        ProductoResponse response = service().create(new ProductoRequest(
                null, 5L, "Fram", null, "Filtro", null, null, null, null));

        assertThat(response.marcaId()).isEqualTo(5L);
        assertThat(response.marcaNombre()).isEqualTo("Bosch");
    }

    @Test
    void create_marcaNombreEnBlanco_quedaSinMarca() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });

        ProductoResponse response = service().create(new ProductoRequest(
                null, null, "   ", null, "Repuesto", null, null, null, null));

        assertThat(response.marcaId()).isNull();
    }

    @Test
    void create_conSkuSinMarca_noValidaUnicidad() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductoResponse response = service().create(
                new ProductoRequest("SUELTO-1", null, null, null, "Repuesto sin marca", null, null, null, null));

        assertThat(response.sku()).isEqualTo("SUELTO-1");
        assertThat(response.marcaId()).isNull();
    }

    @Test
    void create_codigoDuplicado_lanza409() {
        when(productoCodigoRepository.existsByCodigo("DUP")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new ProductoRequest(
                null, null, null, null, "Filtro", null, null,
                List.of(new ProductoCodigoRequest("DUP", null)), null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void agregarCodigo_aProductoExistente() {
        Producto producto = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(producto));
        when(productoCodigoRepository.existsByCodigo("7791234567890")).thenReturn(false);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse response = service().agregarCodigo(
                1L, new ProductoCodigoRequest("7791234567890", "BARRA"));

        assertThat(response.codigos()).extracting(c -> c.codigo()).containsExactly("7791234567890");
    }

    @Test
    void agregarCodigo_duplicado_lanza409() {
        Producto producto = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(producto));
        when(productoCodigoRepository.existsByCodigo("DUP")).thenReturn(true);

        assertThatThrownBy(() -> service().agregarCodigo(1L, new ProductoCodigoRequest("DUP", "BARRA")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void agregarCodigo_productoInexistente_lanza404() {
        when(productoRepository.findWithDetallesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().agregarCodigo(99L, new ProductoCodigoRequest("X", "BARRA")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_existente() {
        Producto producto = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(producto));

        assertThat(service().findById(1L).descripcion()).isEqualTo("Filtro");
    }

    @Test
    void findById_inexistente_lanza404() {
        when(productoRepository.findWithDetallesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAll_devuelvePaginaResumida() {
        Producto conMarca = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO)
                .marca(Marca.builder().id(5L).nombre("Bosch").build())
                .categoria(Categoria.builder().id(7L).nombre("Filtros").build())
                .build();
        Producto suelto = Producto.builder().id(2L).descripcion("Repuesto").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findAllBy(any())).thenReturn(new PageImpl<>(List.of(conMarca, suelto)));

        var page = service().findAll(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(ProductoListItemResponse::marcaNombre)
                .containsExactly("Bosch", null);
    }

    @Test
    void findAll_conTextoSinStock_usaBuscarInteligente() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.buscarInteligente(List.of("filtro"), "filtro", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().findAll("filtro", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).descripcion()).isEqualTo("Filtro");
    }

    @Test
    void findAll_conTextoYConStock_usaBuscarInteligenteConStock() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.buscarInteligenteConStock(List.of("filtro"), "filtro", true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().findAll("filtro", true, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findAll_sinTextoConStock_usaFindConStock() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findConStock(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().findAll(null, true, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findAll_sinTextoSinStock_usaFindSinStock() {
        Producto p = Producto.builder().id(1L).descripcion("Repuesto").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findSinStock(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().findAll(null, false, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).descripcion()).isEqualTo("Repuesto");
    }

    @Test
    void findAll_textoVacio_funcionaComoSinTexto() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findAllBy(any())).thenReturn(new PageImpl<>(List.of(p)));

        var page = service().findAll("  ", null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void buscarPorCodigo_existente() {
        Producto producto = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findByCodigo("7791234567890")).thenReturn(Optional.of(producto));

        assertThat(service().buscarPorCodigo("7791234567890").descripcion()).isEqualTo("Filtro");
    }

    @Test
    void buscarPorCodigo_inexistente_lanza404() {
        when(productoRepository.findByCodigo("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().buscarPorCodigo("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarPorTexto_mapeaAListItem() {
        Producto p = Producto.builder().id(1L).sku("VW-1").descripcion("Aros Volkswagen")
                .estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.buscarInteligente(List.of("volks"), "volks", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().buscarPorTexto("volks", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).descripcion()).isEqualTo("Aros Volkswagen");
    }

    @Test
    void buscarPorTexto_sinTerminos_listaTodo() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findAllBy(any())).thenReturn(new PageImpl<>(List.of(p)));

        var page = service().buscarPorTexto("  ", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void darDeBaja_marcaComoInactivo() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(1L)).thenReturn(Optional.of(p));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        service().darDeBaja(1L);

        assertThat(p.getEstado()).isEqualTo(ProductoEstado.INACTIVO);
    }

    @Test
    void darDeBaja_inexistente_lanza404() {
        when(productoRepository.findWithDetallesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().darDeBaja(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarOpcionalPorCodigo_existente() {
        Producto p = Producto.builder().id(1L).descripcion("Filtro").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findByCodigo("123")).thenReturn(Optional.of(p));

        assertThat(service().buscarOpcionalPorCodigo("123")).isPresent();
    }

    @Test
    void buscarOpcionalPorCodigo_inexistente_devuelveVacio() {
        when(productoRepository.findByCodigo("nope")).thenReturn(Optional.empty());

        assertThat(service().buscarOpcionalPorCodigo("nope")).isEmpty();
    }

    @Test
    void existeCodigo_existente() {
        when(productoCodigoRepository.existsByCodigo("123")).thenReturn(true);

        assertThat(service().existeCodigo("123")).isTrue();
    }

    @Test
    void existeCodigo_inexistente() {
        when(productoCodigoRepository.existsByCodigo("nope")).thenReturn(false);

        assertThat(service().existeCodigo("nope")).isFalse();
    }

    @Test
    void existeCodigo_null_devuelveFalse() {
        assertThat(service().existeCodigo(null)).isFalse();
    }

    @Test
    void matchNormalizado_matchUnico_devuelveProducto() {
        Producto p = Producto.builder().id(1L).sku("813667(STD)").descripcion("Aro")
                .estado(ProductoEstado.ACTIVO)
                .marca(Marca.builder().id(5L).nombre("FP").build()).build();
        when(productoRepository.buscarPorSkuPrefijo("813667", PageRequest.of(0, 30)))
                .thenReturn(List.of(p));

        var result = service().matchNormalizado("813667(STD)", "FP");

        assertThat(result).isPresent();
        assertThat(result.get().sku()).isEqualTo("813667(STD)");
    }

    @Test
    void matchNormalizado_sinAncla_devuelveVacio() {
        var result = service().matchNormalizado("", "Bosch");

        assertThat(result).isEmpty();
    }

    @Test
    void candidatosSimilares_devuelveLista() {
        Producto p1 = Producto.builder().id(1L).sku("813667(STD)").descripcion("Aro STD")
                .estado(ProductoEstado.ACTIVO).build();
        Producto p2 = Producto.builder().id(2L).sku("813667(05)").descripcion("Aro 05")
                .estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.buscarPorSkuPrefijo("813667", PageRequest.of(0, 8)))
                .thenReturn(List.of(p1, p2));

        var candidatos = service().candidatosSimilares("813667");

        assertThat(candidatos).hasSize(2);
    }

    @Test
    void candidatosSimilares_sinAncla_devuelveVacio() {
        var candidatos = service().candidatosSimilares("");

        assertThat(candidatos).isEmpty();
    }

    @Test
    void update_sinSku_noValidaUnicidad() {
        Producto existente = Producto.builder().id(4L).descripcion("viejo").estado(ProductoEstado.ACTIVO).build();
        Marca marca = Marca.builder().id(5L).nombre("Bosch").build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(marcaService.getEntity(5L)).thenReturn(marca);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse r = service().update(4L, new ProductoRequest(
                null, 5L, null, null, "nuevo", null, null, null, null));

        assertThat(r.descripcion()).isEqualTo("nuevo");
        assertThat(r.sku()).isNull();
    }

    @Test
    void update_conEstado_cambiaEstado() {
        Producto existente = Producto.builder().id(4L).descripcion("x").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse r = service().update(4L, new ProductoRequest(
                null, null, null, null, "x", ProductoEstado.BORRADOR, null, null, null));

        assertThat(r.estado()).isEqualTo(ProductoEstado.BORRADOR);
    }

    @Test
    void update_conDetallesExtra_reemplazaDetalles() {
        Producto existente = Producto.builder().id(4L).descripcion("x").estado(ProductoEstado.ACTIVO).build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse r = service().update(4L, new ProductoRequest(
                null, null, null, null, "x", null, Map.of("k", "v"), null, null));

        assertThat(r.detallesExtra()).containsEntry("k", "v");
    }

    @Test
    void update_productoInexistente_lanza404() {
        when(productoRepository.findWithDetallesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(99L, new ProductoRequest(
                null, null, null, null, "x", null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_conCategoria_asignaCategoria() {
        Producto existente = Producto.builder().id(4L).descripcion("x").estado(ProductoEstado.ACTIVO).build();
        Categoria cat = Categoria.builder().id(7L).nombre("Filtros").build();
        when(productoRepository.findWithDetallesById(4L)).thenReturn(Optional.of(existente));
        when(categoriaService.getEntity(7L)).thenReturn(cat);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse r = service().update(4L, new ProductoRequest(
                null, null, null, 7L, "x", null, null, null, null));

        assertThat(r.categoriaId()).isEqualTo(7L);
    }
}
