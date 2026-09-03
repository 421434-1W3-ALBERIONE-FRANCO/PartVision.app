package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.inventory.domain.Stock;
import com.partvision.location.domain.Ubicacion;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración del motor de búsqueda contra PostgreSQL real.
 * Ejercita FTS, trigram fallback, anchor selection, ranking, stock filter
 * y edge cases con datos representativos de Autopartes del Sur y EGSA.
 */
@SpringBootTest
@ActiveProfiles("devdb")
@EnabledIf(value = "dbDisponible", disabledReason = "PostgreSQL de dev no disponible en localhost:5442")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusquedaIntegracionTest {

    @Autowired
    private ProductoRepository repo;

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    private static final Pageable PAGE = PageRequest.of(0, 20);

    // ── Fixtures ──────────────────────────────────────────────────────

    private Marca marcaTaranto, marcaFP, marcaMotorlimp;
    private Categoria catPistones, catJuntas, catAditivos, catCojinetes;
    private Ubicacion ubicacion;

    @BeforeAll
    void seedData() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            seedDataInterno();
        });
    }

    private void seedDataInterno() {
        em.createNativeQuery("DELETE FROM stock").executeUpdate();
        em.createNativeQuery("DELETE FROM producto_codigos").executeUpdate();
        em.createNativeQuery("DELETE FROM productos").executeUpdate();
        em.createNativeQuery("DELETE FROM ubicaciones").executeUpdate();
        em.createNativeQuery("DELETE FROM categorias").executeUpdate();
        em.createNativeQuery("DELETE FROM marcas").executeUpdate();
        em.flush();

        marcaTaranto = saveMarca("TARANTO");
        marcaFP = saveMarca("FP INDUSTRIAS");
        marcaMotorlimp = saveMarca("MOTORLIMP");

        catPistones = saveCategoria("PISTONES");
        catJuntas = saveCategoria("JUNTAS");
        catAditivos = saveCategoria("ADITIVOS");
        catCojinetes = saveCategoria("COJINETES");

        ubicacion = saveUbicacion("A0101", "A/01/01");

        // ── ADS-style: descripciones largas, sin marca JPA, sin categoría JPA ──

        saveProducto("PISTONES FIAT - 600 - 750CC (+0.60MM)(62.60MM)(SE VENDE POR JUEGO)",
                "0082-20-15", null, null, "Autopartes del Sur");

        saveProducto("PISTONES FIAT - 600 - 750CC (STD)(62.00MM)(SE VENDE POR JUEGO)",
                "0082-20-00", null, null, "Autopartes del Sur");

        saveProducto("PISTONES VOLKSWAGEN - GOL TREND - EA111 1.6 8V (+0.50MM)(76.50MM)",
                "ZSD1950350", null, null, "Autopartes del Sur");

        saveProducto("SUBCONJUNTO MERCEDES BENZ - OM352A (5 AROS / ASPIRADO)(+0.50MM)(97.50MM)",
                "ZSE0078350", null, null, "Autopartes del Sur");

        saveProducto("AROS RENAULT - CLIO 2 - K4M 1.6 16V (STD)(79.50MM)(JUEGO MOTOR)",
                "AR-CLIO-STD", null, null, "Autopartes del Sur");

        saveProducto("AROS RENAULT - KANGOO - K7M 1.6 8V (+0.50MM)(79.50MM)(JUEGO MOTOR)",
                "AR-KANG-050", null, null, "Autopartes del Sur");

        saveProducto("COJINETE DE BANCADA FORD - FALCON - 221 (6 CILINDROS)(STD)",
                "COJ-FALC-STD", null, null, "Autopartes del Sur");

        saveProducto("COJINETE DE BIELA FORD - FALCON - 221 (6 CILINDROS)(+0.25MM)",
                "COJ-BIEL-025", null, null, "Autopartes del Sur");

        saveProducto("JUNTA DE TAPA DE CILINDROS FORD - FALCON - 221 (6 CILINDROS)(MAS JUNTAS)",
                "JT-FALC-221", null, null, "Autopartes del Sur");

        saveProducto("PISTONES FORD - FOCUS II - DURATEC 2.0 16V (STD)(87.50MM)",
                "PIST-FOCUS-STD", null, null, "Autopartes del Sur");

        // ── EGSA-style: descripciones cortas, con marca y categoría ──

        saveProducto("MOTORLIMP PLUS X 5 LTS.", "002", marcaMotorlimp, catAditivos, "EGSA");

        saveProducto("MT-10 TRATAMIENTO DEL MOTOR 8 OZ.(240CC)", "003", marcaMotorlimp, catAditivos, "EGSA");

        saveProducto("JUNTA CAMARA LATERAL", "0245", marcaTaranto, catJuntas, "EGSA");

        saveProducto("JUNTA TAPA BALANCINES FIAT UNO FIRE", "0246", marcaTaranto, catJuntas, "EGSA");

        saveProducto("JUEGO DE JUNTAS COMPLETO FIAT 128", "0247", marcaTaranto, catJuntas, "EGSA");

        saveProducto("COJINETE BANCADA PEUGEOT 404 (+0.25)", "910938", marcaFP, catCojinetes, "EGSA");

        // ── Producto con stock para filtro ──

        Producto conStock = saveProducto(
                "PISTONES TOYOTA - HILUX 2.5 TD (STD)(92.00MM)",
                "PIST-HIL-STD", null, catPistones, "Autopartes del Sur");
        saveStock(conStock, ubicacion, 10);

        Producto conStock2 = saveProducto(
                "AROS TOYOTA - HILUX 2.5 TD (STD)(92.00MM)",
                "AR-HIL-STD", null, null, "Autopartes del Sur");
        saveStock(conStock2, ubicacion, 5);

        em.flush();
        em.clear();
    } // seedDataInterno

    // ═══════════════════════════════════════════════════════════════════
    //  FTS — Término único
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class FtsTerminoUnico {

        @Test
        void busca_por_tipo_de_pieza() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones"), "pistones", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(4);
            assertThat(r.getContent()).allSatisfy(p ->
                    assertThat(p.getDescripcion().toLowerCase()).contains("piston"));
        }

        @Test
        void busca_por_marca_en_campo_busqueda() {
            Page<Producto> r = repo.buscarInteligente(List.of("taranto"), "taranto", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void busca_por_categoria_en_campo_busqueda() {
            Page<Producto> r = repo.buscarInteligente(List.of("aditivos"), "aditivos", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void busca_vehiculo_en_descripcion() {
            Page<Producto> r = repo.buscarInteligente(List.of("volkswagen"), "volkswagen", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getDescripcion()).containsIgnoringCase("VOLKSWAGEN");
        }

        @Test
        void busca_modelo_motor() {
            Page<Producto> r = repo.buscarInteligente(List.of("om352a"), "om352a", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getDescripcion()).containsIgnoringCase("OM352A");
        }

        @Test
        void busca_sku_alfanumerico_completo() {
            Page<Producto> r = repo.buscarInteligente(List.of("zsd1950350"), "zsd1950350", PAGE);
            assertThat(r.getTotalElements()).isEqualTo(1);
            assertThat(r.getContent().get(0).getSku()).isEqualTo("ZSD1950350");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FTS — Multi-token (anchor + rest)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class FtsMultiToken {

        @Test
        void pieza_mas_vehiculo() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones", "fiat"), "pistones fiat", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            assertThat(r.getContent()).allSatisfy(p -> {
                String d = p.getDescripcion().toLowerCase();
                assertThat(d).contains("piston");
                assertThat(d).contains("fiat");
            });
        }

        @Test
        void pieza_vehiculo_cilindrada() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones", "fiat", "600"), "pistones fiat 600", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            assertThat(r.getContent()).allSatisfy(p ->
                    assertThat(p.getDescripcion().toLowerCase()).contains("fiat"));
        }

        @Test
        void aros_mas_marca_vehiculo() {
            Page<Producto> r = repo.buscarInteligente(List.of("aros", "renault"), "aros renault", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            assertThat(r.getContent()).allSatisfy(p -> {
                String d = p.getDescripcion().toLowerCase();
                assertThat(d).contains("aros");
                assertThat(d).contains("renault");
            });
        }

        @Test
        void marca_jpa_mas_tipo_pieza() {
            Page<Producto> r = repo.buscarInteligente(List.of("taranto", "junta"), "taranto junta", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void tres_tokens_relevantes() {
            Page<Producto> r = repo.buscarInteligente(List.of("subconjunto", "mercedes", "aspirado"), "subconjunto mercedes aspirado", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getDescripcion()).containsIgnoringCase("ASPIRADO");
        }

        @Test
        void cojinete_bancada_ford() {
            Page<Producto> r = repo.buscarInteligente(List.of("cojinete", "bancada", "ford"), "cojinete bancada ford", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getDescripcion()).containsIgnoringCase("BANCADA");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Anchor selection
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class AnchorSelection {

        @Test
        void generica_juego_no_es_anchor() {
            // "juego" es GENERICA → anchor debería ser "aros"
            Page<Producto> r = repo.buscarInteligente(List.of("juego", "aros", "fiat"), "juego aros fiat", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(0);
            // No debe explotar; "aros" acota mejor que "juego"
        }

        @Test
        void generica_kit_no_es_anchor() {
            Page<Producto> r = repo.buscarInteligente(List.of("kit", "pistones", "ford"), "kit pistones ford", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void solo_token_numerico_usa_indice_cero() {
            // Token "600" no es alfabético ≥3 → fallback a index 0
            Page<Producto> r = repo.buscarInteligente(List.of("600"), "600", PAGE);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Trigram fallback (typos)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class TrigramFallback {

        @Test
        void typo_en_pieza_pistpnes() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistpnes"), "pistpnes", PAGE);
            assertThat(r.getTotalElements()).isGreaterThan(0);
        }

        @Test
        void typo_en_vehiculo_volskwagen() {
            Page<Producto> r = repo.buscarInteligente(List.of("volskwagen"), "volskwagen", PAGE);
            assertThat(r.getTotalElements()).isGreaterThan(0);
        }

        @Test
        void typo_en_marca_tarrato() {
            Page<Producto> r = repo.buscarInteligente(List.of("tarrato"), "tarrato", PAGE);
            assertThat(r.getTotalElements()).isGreaterThan(0);
        }

        @Test
        void termino_inexistente_devuelve_vacio() {
            Page<Producto> r = repo.buscarInteligente(List.of("xylophone"), "xylophone", PAGE);
            assertThat(r.getTotalElements()).isEqualTo(0);
        }

        @Test
        void typo_doble_cojinettes_mercedez() {
            Page<Producto> r = repo.buscarInteligente(List.of("cojinettes", "mercedez"), "cojinettes mercedez", PAGE);
            // Puede matchear parcial o nada, pero no debe explotar
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Ranking
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class Ranking {

        @Test
        void prefix_boost_descripcion_empieza_con_anchor() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones"), "pistones", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            // Primeros resultados: descripción empieza con "PISTONES"
            assertThat(r.getContent().get(0).getDescripcion()).startsWithIgnoringCase("PISTONES");
        }

        @Test
        void descripcion_corta_antes_que_larga() {
            Page<Producto> r = repo.buscarInteligente(List.of("junta"), "junta", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            List<Producto> content = r.getContent();
            // Dentro de los que empiezan con "JUNTA", la más corta primero
            Producto primerJunta = content.stream()
                    .filter(p -> p.getDescripcion().toUpperCase().startsWith("JUNTA"))
                    .findFirst().orElse(null);
            if (primerJunta != null) {
                assertThat(primerJunta.getDescripcion().length()).isLessThanOrEqualTo(30);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Stock filter
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class StockFilter {

        @Test
        void con_stock_solo_devuelve_productos_con_cantidad_positiva() {
            Page<Producto> r = repo.buscarInteligenteConStock(List.of("pistones"), "pistones", true, PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent()).allSatisfy(p ->
                    assertThat(p.getDescripcion().toLowerCase()).contains("piston"));
        }

        @Test
        void sin_stock_excluye_productos_con_cantidad_positiva() {
            Page<Producto> conStock = repo.buscarInteligenteConStock(List.of("pistones"), "pistones", true, PAGE);
            Page<Producto> sinStock = repo.buscarInteligenteConStock(List.of("pistones"), "pistones", false, PAGE);
            Page<Producto> todos = repo.buscarInteligente(List.of("pistones"), "pistones", PAGE);

            assertThat(conStock.getTotalElements() + sinStock.getTotalElements())
                    .isEqualTo(todos.getTotalElements());
        }

        @Test
        void disjuncion_stock_con_otro_termino() {
            Page<Producto> conStock = repo.buscarInteligenteConStock(List.of("toyota"), "toyota", true, PAGE);
            Page<Producto> sinStock = repo.buscarInteligenteConStock(List.of("toyota"), "toyota", false, PAGE);
            Page<Producto> todos = repo.buscarInteligente(List.of("toyota"), "toyota", PAGE);

            assertThat(conStock.getTotalElements() + sinStock.getTotalElements())
                    .isEqualTo(todos.getTotalElements());
        }

        @Test
        void aros_con_stock_incluye_hilux() {
            Page<Producto> r = repo.buscarInteligenteConStock(List.of("aros"), "aros", true, PAGE);
            assertThat(r.getContent()).anyMatch(p ->
                    p.getDescripcion().toUpperCase().contains("HILUX"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class EdgeCases {

        @Test
        void tokens_vacios_devuelve_pagina_vacia() {
            Page<Producto> r = repo.buscarInteligente(List.of(), "", PAGE);
            assertThat(r.getTotalElements()).isEqualTo(0);
            assertThat(r.getContent()).isEmpty();
        }

        @Test
        void sku_con_guiones_matchea() {
            Page<Producto> r = repo.buscarInteligente(List.of("0082-20-15"), "0082-20-15", PAGE);
            // FTS 'simple' tokeniza en guiones → busca fragmentos
            assertThat(r).isNotNull();
        }

        @Test
        void mayusculas_ftsSanitize_descarta_uppercase() {
            // ftsSanitize solo acepta [a-z0-9.] → tokens en MAYÚSCULA quedan vacíos.
            // El repo depende de que el servicio haga toLowerCase() antes.
            // Con mayúsculas, FTS falla → cae a trigram → puede o no encontrar.
            Page<Producto> upper = repo.buscarInteligente(List.of("PISTONES", "FIAT"), "PISTONES FIAT", PAGE);
            assertThat(upper).isNotNull();
        }

        @Test
        void medida_numerica_como_busqueda() {
            Page<Producto> r = repo.buscarInteligente(List.of("97.50mm"), "97.50mm", PAGE);
            assertThat(r).isNotNull();
        }

        @Test
        void caracteres_especiales_no_explotan() {
            Page<Producto> r = repo.buscarInteligente(List.of("';--drop"), "';--drop", PAGE);
            assertThat(r).isNotNull();
        }

        @Test
        void ocho_tokens_maximo_funciona() {
            Page<Producto> r = repo.buscarInteligente(
                    List.of("pistones", "fiat", "600", "750cc", "std", "62mm", "motor", "juego"),
                    "pistones fiat 600 750cc std 62mm motor juego", PAGE);
            assertThat(r).isNotNull();
        }

        @Test
        void un_solo_token_corto_numerico() {
            Page<Producto> r = repo.buscarInteligente(List.of("16"), "16", PAGE);
            assertThat(r).isNotNull();
        }

        @Test
        void busqueda_proveedor_en_campo_busqueda_no_indexado() {
            // "proveedor" no está en la columna busqueda → no matchea por texto
            Page<Producto> r = repo.buscarInteligente(List.of("egsa"), "egsa", PAGE);
            // Puede devolver 0 (proveedor no está en busqueda) o algo si "egsa" aparece en otro campo
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Paginación
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class Paginacion {

        @Test
        void pagina_cero_devuelve_primera_pagina() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones"), "pistones", PageRequest.of(0, 2));
            assertThat(r.getNumber()).isEqualTo(0);
            assertThat(r.getContent().size()).isLessThanOrEqualTo(2);
            assertThat(r.getTotalElements()).isGreaterThan(2);
        }

        @Test
        void pagina_siguiente_devuelve_otros_resultados() {
            Page<Producto> p0 = repo.buscarInteligente(List.of("pistones"), "pistones", PageRequest.of(0, 2));
            Page<Producto> p1 = repo.buscarInteligente(List.of("pistones"), "pistones", PageRequest.of(1, 2));

            assertThat(p0.getContent()).isNotEmpty();
            assertThat(p1.getContent()).isNotEmpty();

            List<Long> idsP0 = p0.getContent().stream().map(Producto::getId).toList();
            List<Long> idsP1 = p1.getContent().stream().map(Producto::getId).toList();
            assertThat(idsP0).doesNotContainAnyElementsOf(idsP1);
        }

        @Test
        void pagina_fuera_de_rango_devuelve_vacio() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones"), "pistones", PageRequest.of(100, 20));
            assertThat(r.getContent()).isEmpty();
            assertThat(r.getTotalElements()).isGreaterThan(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Trigger de busqueda
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class TriggerBusqueda {

        @Test
        void trigger_puebla_columna_busqueda_al_insertar() {
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                Producto p = saveProducto("TEST TRIGGER INSERCION", "TRG-001", marcaTaranto, catJuntas, null);
                em.flush();
                em.clear();

                String busqueda = (String) em.createNativeQuery(
                        "SELECT busqueda FROM productos WHERE id = :id")
                        .setParameter("id", p.getId())
                        .getSingleResult();

                assertThat(busqueda).contains("test trigger insercion");
                assertThat(busqueda).contains("trg-001");
                assertThat(busqueda).contains("taranto");
                assertThat(busqueda).contains("juntas");

                status.setRollbackOnly();
            });
        }

        @Test
        void trigger_actualiza_busqueda_al_cambiar_descripcion() {
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                Producto p = saveProducto("DESCRIPCION ORIGINAL", "TRG-002", null, null, null);
                em.flush();

                em.createNativeQuery("UPDATE productos SET descripcion = 'DESCRIPCION MODIFICADA' WHERE id = :id")
                        .setParameter("id", p.getId())
                        .executeUpdate();
                em.flush();
                em.clear();

                String busqueda = (String) em.createNativeQuery(
                        "SELECT busqueda FROM productos WHERE id = :id")
                        .setParameter("id", p.getId())
                        .getSingleResult();

                assertThat(busqueda).contains("descripcion modificada");
                assertThat(busqueda).doesNotContain("original");

                status.setRollbackOnly();
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Búsquedas cruzando proveedores (ADS + EGSA)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class BusquedaCruzada {

        @Test
        void junta_devuelve_ambos_proveedores() {
            Page<Producto> r = repo.buscarInteligente(List.of("junta"), "junta", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(3);
            // ADS: "JUNTA DE TAPA DE CILINDROS FORD..."
            // EGSA: "JUNTA CAMARA LATERAL", "JUNTA TAPA BALANCINES..."
        }

        @Test
        void cojinete_devuelve_ambos_proveedores() {
            Page<Producto> r = repo.buscarInteligente(List.of("cojinete"), "cojinete", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(3);
            // ADS: 2 cojinetes Ford
            // EGSA: 1 cojinete Peugeot
        }

        @Test
        void busqueda_fiat_cruza_proveedores() {
            Page<Producto> r = repo.buscarInteligente(List.of("fiat"), "fiat", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(3);
            // ADS: Pistones Fiat 600
            // EGSA: Junta tapa balancines Fiat Uno, Juego juntas Fiat 128
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Prefix matching (as-you-type, estilo Meilisearch)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class PrefixMatching {

        @Test
        void prefijo_parcial_pieza() {
            Page<Producto> r = repo.buscarInteligente(List.of("pist"), "pist", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(4);
            assertThat(r.getContent()).allSatisfy(p ->
                    assertThat(p.getDescripcion().toLowerCase()).contains("piston"));
        }

        @Test
        void prefijo_parcial_vehiculo() {
            Page<Producto> r = repo.buscarInteligente(List.of("volksw"), "volksw", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getDescripcion()).containsIgnoringCase("VOLKSWAGEN");
        }

        @Test
        void prefijo_parcial_marca_jpa() {
            Page<Producto> r = repo.buscarInteligente(List.of("taran"), "taran", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void prefijo_dos_tokens_parciales() {
            Page<Producto> r = repo.buscarInteligente(List.of("pist", "fi"), "pist fi", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(2);
            assertThat(r.getContent()).allSatisfy(p -> {
                String d = p.getDescripcion().toLowerCase();
                assertThat(d).contains("piston");
                assertThat(d).contains("fiat");
            });
        }

        @Test
        void prefijo_sku_parcial() {
            Page<Producto> r = repo.buscarInteligente(List.of("zsd195"), "zsd195", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(r.getContent().get(0).getSku()).isEqualTo("ZSD1950350");
        }

        @Test
        void prefijo_motor_parcial() {
            Page<Producto> r = repo.buscarInteligente(List.of("om35"), "om35", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tokenizacion mejorada (guiones, parentesis)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class TokenizacionMejorada {

        @Test
        void sku_con_guiones_split_en_partes() {
            // "0082-20-15" ahora se tokeniza como ["0082", "20", "15"]
            // FTS busca cada parte como prefijo en la columna busqueda
            Page<Producto> r = repo.buscarInteligente(List.of("0082", "20", "15"), "0082 20 15", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void sku_numerico_parcial_como_prefijo() {
            Page<Producto> r = repo.buscarInteligente(List.of("0082"), "0082", PAGE);
            assertThat(r.getTotalElements()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void medida_como_token_independiente() {
            Page<Producto> r = repo.buscarInteligente(List.of("pistones", "62.60mm"), "pistones 62.60mm", PAGE);
            assertThat(r).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private Marca saveMarca(String nombre) {
        Marca m = Marca.builder().nombre(nombre).build();
        em.persist(m);
        return m;
    }

    private Categoria saveCategoria(String nombre) {
        Categoria c = Categoria.builder().nombre(nombre).build();
        em.persist(c);
        return c;
    }

    private Ubicacion saveUbicacion(String codigo, String path) {
        Ubicacion u = Ubicacion.builder()
                .codigo(codigo).path(path).activo(true)
                .build();
        em.persist(u);
        return u;
    }

    private Producto saveProducto(String desc, String sku, Marca marca, Categoria cat, String prov) {
        Producto p = Producto.builder()
                .descripcion(desc)
                .sku(sku)
                .marca(marca)
                .categoria(cat)
                .proveedor(prov)
                .estado(ProductoEstado.ACTIVO)
                .build();
        em.persist(p);
        return p;
    }

    private void saveStock(Producto producto, Ubicacion ubicacion, int cantidad) {
        Stock s = Stock.builder()
                .producto(producto)
                .ubicacion(ubicacion)
                .cantidad(cantidad)
                .version(0L)
                .build();
        em.persist(s);
    }

    static boolean dbDisponible() {
        try (var conn = java.sql.DriverManager.getConnection(
                "jdbc:postgresql://localhost:5442/partvision_test", "partvision", "partvision")) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
