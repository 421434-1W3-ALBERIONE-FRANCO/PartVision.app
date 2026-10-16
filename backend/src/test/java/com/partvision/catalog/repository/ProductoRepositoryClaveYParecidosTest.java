package com.partvision.catalog.repository;

import com.partvision.catalog.domain.CodigoProducto;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos consultas nuevas del 2026-10-15 contra PostgreSQL real: la que encuentra un codigo
 * escrito de otra forma (expresion del indice V34) y la de descripciones parecidas (pg_trgm).
 * Cada test corre en una transaccion que se deshace: no deja nada en la base.
 */
@SpringBootTest
@ActiveProfiles("devdb")
@EnabledIf(value = "dbDisponible", disabledReason = "PostgreSQL de dev no disponible en localhost:5442")
@Transactional
class ProductoRepositoryClaveYParecidosTest {

    static boolean dbDisponible() {
        return BusquedaIntegracionTest.dbDisponible();
    }

    @Autowired
    private ProductoRepository repo;

    @Autowired
    private EntityManager em;

    private Producto guardar(String sku, String descripcion, String marca, String proveedor) {
        Marca m = Marca.builder().nombre(marca).build();
        em.persist(m);
        Producto p = Producto.builder().sku(sku).descripcion(descripcion).marca(m).proveedor(proveedor)
                .estado(ProductoEstado.ACTIVO).build();
        em.persist(p);
        em.flush();
        return p;
    }

    /** La clave que arma Java tiene que dar lo mismo que la expresion de la base. */
    @Test
    void findByClaveSkuIn_encuentraElCodigoEscritoDeOtraForma() {
        Producto pk = guardar("ZQPK2000", "CAMISA PACKSON PRUEBA", "PACKSON-ZQ", "EGSA");
        Producto conPuntos = guardar("ZQ10.272", "SOPORTE PRUEBA", "SCHADECK-ZQ", "EGSA");

        List<Producto> r = repo.findByClaveSkuIn(List.of(
                CodigoProducto.clave("ZQPK-2000"), CodigoProducto.clave("zq 10272")));

        assertThat(r).extracting(Producto::getId).containsExactlyInAnyOrder(pk.getId(), conPuntos.getId());
    }

    @Test
    void findByClaveSkuIn_noTraeOtrosCodigos() {
        guardar("ZQPK2001", "CAMISA PACKSON PRUEBA", "PACKSON-ZQ2", "EGSA");

        assertThat(repo.findByClaveSkuIn(List.of(CodigoProducto.clave("ZQPK-2000")))).isEmpty();
    }

    @Test
    void parecidosPorDescripcion_ponePrimeroLaDescripcionIgual() {
        Producto igual = guardar("ZQBV1", "BALANCIN ZQWX MITSUBISHI 4D56 ADMISION PRUEBA", "ZQ-M1", "Autopartes del Sur");
        guardar("ZQBV2", "BALANCIN ZQWX MITSUBISHI 4D56 ESCAPE PRUEBA", "ZQ-M2", "Autopartes del Sur");

        List<ProductoRepositoryCustom.Parecido> r =
                repo.parecidosPorDescripcion("balancin zqwx mitsubishi 4d56 admision prueba", 5);

        assertThat(r).isNotEmpty();
        assertThat(r.get(0).productoId()).isEqualTo(igual.getId());
        assertThat(r.get(0).parecido()).isGreaterThan(0.95);
        assertThat(r).extracting(ProductoRepositoryCustom.Parecido::parecido).isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void parecidosPorDescripcion_sinTexto_noConsulta() {
        assertThat(repo.parecidosPorDescripcion("  ", 5)).isEmpty();
        assertThat(repo.parecidosPorDescripcion(null, 5)).isEmpty();
    }
}
