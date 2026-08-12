package com.partvision.catalog.service;

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

    private ProductoService service() {
        return new ProductoService(productoRepository, productoCodigoRepository, marcaService, categoriaService,
                new ProductoMatcher(), stockRepository);
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
        when(productoRepository.buscarPorTexto("volks", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(p)));

        var page = service().buscarPorTexto("volks", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).descripcion()).isEqualTo("Aros Volkswagen");
    }
}
