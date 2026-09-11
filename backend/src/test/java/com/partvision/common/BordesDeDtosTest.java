package com.partvision.common;

import com.partvision.ai.search.SearchFilters;
import com.partvision.ai.search.SearchIntent;
import com.partvision.ai.search.SearchInterpretation;
import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoListItemResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los DTO que normalizan datos incompletos. Un producto sin marca o sin categoria es normal
 * en el catalogo, y una interpretacion de la IA puede venir con campos faltantes.
 */
class BordesDeDtosTest {

    private static Producto producto(Marca marca, Categoria categoria) {
        Producto p = new Producto();
        p.setId(1L);
        p.setSku("SKU-1");
        p.setDescripcion("Filtro de aceite");
        p.setEstado(ProductoEstado.ACTIVO);
        p.setMarca(marca);
        p.setCategoria(categoria);
        return p;
    }

    private static Marca marca() {
        Marca m = new Marca();
        m.setNombre("Mahle");
        return m;
    }

    private static Categoria categoria() {
        Categoria c = new Categoria();
        c.setNombre("Filtros");
        return c;
    }

    @Test
    void productoSinMarcaNiCategoria_quedaConNulos() {
        ProductoListItemResponse r = ProductoListItemResponse.from(producto(null, null));

        assertThat(r.marcaNombre()).isNull();
        assertThat(r.categoriaNombre()).isNull();
        assertThat(r.stockTotal()).isZero();
        assertThat(r.ubicaciones()).isEmpty();
    }

    @Test
    void productoConMarcaYCategoria_losCopia() {
        ProductoListItemResponse r = ProductoListItemResponse.from(producto(marca(), categoria()));

        assertThat(r.marcaNombre()).isEqualTo("Mahle");
        assertThat(r.categoriaNombre()).isEqualTo("Filtros");
    }

    @Test
    void conStock_sinMarcaNiCategoria_quedaConNulos() {
        ProductoListItemResponse r = ProductoListItemResponse.from(producto(null, null), 7,
                List.of(new ProductoListItemResponse.StockUbicacion("A-01", 7)));

        assertThat(r.marcaNombre()).isNull();
        assertThat(r.categoriaNombre()).isNull();
        assertThat(r.stockTotal()).isEqualTo(7);
        assertThat(r.ubicaciones()).hasSize(1);
    }

    @Test
    void conStock_conMarcaYCategoria_losCopia() {
        ProductoListItemResponse r = ProductoListItemResponse.from(producto(marca(), categoria()), 3, List.of());

        assertThat(r.marcaNombre()).isEqualTo("Mahle");
        assertThat(r.categoriaNombre()).isEqualTo("Filtros");
    }

    /** Lo que devuelve el modelo puede venir incompleto: nunca tiene que quedar un null adentro. */
    @Test
    void interpretacionConCamposNulos_seNormaliza() {
        SearchInterpretation i = new SearchInterpretation("q", "q", null, null, null, null, 0.5);

        assertThat(i.terms()).isEmpty();
        assertThat(i.synonyms()).isEmpty();
        assertThat(i.intent()).isEqualTo(SearchIntent.UNKNOWN);
        assertThat(i.filters()).isEqualTo(SearchFilters.EMPTY);
    }

    @Test
    void interpretacionCompleta_seRespeta() {
        SearchInterpretation i = new SearchInterpretation("q", "q", List.of("a"), List.of("b"),
                SearchIntent.SKU_SEARCH, new SearchFilters("Mahle", "Filtros", true), 0.9);

        assertThat(i.terms()).containsExactly("a");
        assertThat(i.synonyms()).containsExactly("b");
        assertThat(i.intent()).isEqualTo(SearchIntent.SKU_SEARCH);
        assertThat(i.filters().brand()).isEqualTo("Mahle");
    }
}
