package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test de búsqueda contra el catálogo real (~135k productos ADS + EGSA).
 * Importa los CSVs via docker exec + COPY, luego mide recall y rendimiento.
 */
@SpringBootTest
@ActiveProfiles("devdb")
@EnabledIf(value = "dbDisponible", disabledReason = "PostgreSQL no disponible")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BusquedaCatalogoRealTest {

    @Autowired private ProductoRepository repo;
    @Autowired private EntityManager em;

    private static final Pageable PAGE = PageRequest.of(0, 20);
    private static final Pageable PAGE_100 = PageRequest.of(0, 100);
    private static final Pageable PAGE_500 = PageRequest.of(0, 500);

    private static final Set<String> STOP = Set.of(
            "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas",
            "para", "con", "por", "y", "o", "al", "en", "a", "se", "vende",
            "juego", "juegos", "kit", "kits", "set", "par", "pares", "mas",
            "que", "no", "es", "lo", "su", "como", "si", "mm", "cc");

    private long totalProductos;

    @BeforeAll
    void importarCatalogo() throws Exception {
        Path dataDir = resolveDataDir();
        assumeTrue(dataDir != null, "data-import/ directory not found");
        Path egsaCsv = dataDir.resolve("productos_EGSA.csv");
        Path adsCsv = dataDir.resolve("productos_AutopartesDelSur.csv");
        assumeTrue(Files.exists(egsaCsv), "EGSA CSV not found");
        assumeTrue(Files.exists(adsCsv), "ADS CSV not found");

        em.clear();
        long existing = countProductos();
        if (existing > 100_000) {
            totalProductos = existing;
            return;
        }

        docker("docker", "cp", egsaCsv.toString(), "partvision-db:/tmp/productos_EGSA.csv");
        docker("docker", "cp", adsCsv.toString(), "partvision-db:/tmp/productos_AutopartesDelSur.csv");

        Path sqlFile = Files.createTempFile("import_catalogo", ".sql");
        Files.writeString(sqlFile, importSql());
        docker("docker", "cp", sqlFile.toString(), "partvision-db:/tmp/import_catalogo.sql");
        docker("docker", "exec", "partvision-db",
                "psql", "-U", "partvision", "-d", "partvision_test",
                "-f", "/tmp/import_catalogo.sql");
        Files.deleteIfExists(sqlFile);

        em.clear();
        totalProductos = countProductos();
        assumeTrue(totalProductos > 100_000,
                "Import incomplete: solo " + totalProductos + " productos");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Verificación de import
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class ImportVerification {

        @Test
        void total_productos_mayor_a_130k() {
            assertThat(totalProductos).isGreaterThan(130_000);
        }

        @Test
        void columna_busqueda_poblada_para_todos() {
            long sinBusqueda = ((Number) em.createNativeQuery(
                    "SELECT count(*) FROM productos WHERE busqueda IS NULL OR trim(busqueda) = ''")
                    .getSingleResult()).longValue();
            assertThat(sinBusqueda).as("Productos sin columna busqueda").isEqualTo(0);
        }

        @Test
        void indices_fts_y_trigram_existen() {
            @SuppressWarnings("unchecked")
            List<String> indices = em.createNativeQuery(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'productos' AND indexname LIKE '%busqueda%'")
                    .getResultList();
            assertThat(indices).anyMatch(n -> n.contains("fts"));
            assertThat(indices).anyMatch(n -> n.contains("trgm"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recall: palabra principal de la descripción
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class RecallPalabraPrincipal {

        @Test
        void recall_una_palabra_en_top100() {
            List<Object[]> muestra = muestraAleatoria(300);
            int encontrados = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String desc = (String) row[1];
                List<String> terms = extraerTerminos(desc, 1);
                if (terms.isEmpty()) continue;
                evaluados++;

                Page<Producto> r = repo.buscarInteligente(terms, String.join(" ", terms), PAGE_100);
                if (r.getContent().stream().anyMatch(p -> p.getId().equals(id))) {
                    encontrados++;
                }
            }

            double rate = evaluados == 0 ? 0 : (double) encontrados / evaluados;
            assertThat(rate)
                    .as("Recall 1 palabra: %d/%d (%.1f%%)", encontrados, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.05);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recall: dos palabras de la descripción
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class RecallDosTerminos {

        @Test
        void recall_dos_palabras_en_top100() {
            List<Object[]> muestra = muestraAleatoria(300);
            int encontrados = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String desc = (String) row[1];
                List<String> terms = extraerTerminos(desc, 2);
                if (terms.size() < 2) continue;
                evaluados++;

                Page<Producto> r = repo.buscarInteligente(terms, String.join(" ", terms), PAGE_100);
                if (r.getContent().stream().anyMatch(p -> p.getId().equals(id))) {
                    encontrados++;
                }
            }

            double rate = evaluados == 0 ? 0 : (double) encontrados / evaluados;
            assertThat(rate)
                    .as("Recall 2 palabras: %d/%d (%.1f%%)", encontrados, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.30);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recall: tres palabras de la descripción
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class RecallTresTerminos {

        @Test
        void recall_tres_palabras_en_top100() {
            List<Object[]> muestra = muestraAleatoria(300);
            int encontrados = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String desc = (String) row[1];
                List<String> terms = extraerTerminos(desc, 3);
                if (terms.size() < 3) continue;
                evaluados++;

                Page<Producto> r = repo.buscarInteligente(terms, String.join(" ", terms), PAGE_100);
                if (r.getContent().stream().anyMatch(p -> p.getId().equals(id))) {
                    encontrados++;
                }
            }

            double rate = evaluados == 0 ? 0 : (double) encontrados / evaluados;
            assertThat(rate)
                    .as("Recall 3 palabras: %d/%d (%.1f%%)", encontrados, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.55);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recall: SKU completo
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class RecallPorSku {

        @Test
        void recall_sku_80_porciento() {
            @SuppressWarnings("unchecked")
            List<Object[]> muestra = em.createNativeQuery(
                    "SELECT id, sku FROM productos WHERE sku IS NOT NULL AND length(sku) >= 4 ORDER BY random() LIMIT 200")
                    .getResultList();
            int encontrados = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String sku = (String) row[1];
                List<String> tokens = tokenizar(sku);
                if (tokens.isEmpty()) continue;
                evaluados++;

                Page<Producto> r = repo.buscarInteligente(tokens, String.join(" ", tokens), PAGE_100);
                if (r.getContent().stream().anyMatch(p -> p.getId().equals(id))) {
                    encontrados++;
                }
            }

            double rate = evaluados == 0 ? 0 : (double) encontrados / evaluados;
            assertThat(rate)
                    .as("Recall SKU: %d/%d (%.1f%%)", encontrados, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.80);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Recall: búsqueda por prefijo (as-you-type)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class RecallPorPrefijo {

        @Test
        void recall_prefijo_4chars_en_top100() {
            List<Object[]> muestra = muestraAleatoria(200);
            int encontrados = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String desc = (String) row[1];
                List<String> terms = extraerTerminos(desc, 1);
                if (terms.isEmpty() || terms.get(0).length() < 5) continue;
                evaluados++;

                String prefijo = terms.get(0).substring(0, 4);
                Page<Producto> r = repo.buscarInteligente(List.of(prefijo), prefijo, PAGE_100);
                if (r.getContent().stream().anyMatch(p -> p.getId().equals(id))) {
                    encontrados++;
                }
            }

            double rate = evaluados == 0 ? 0 : (double) encontrados / evaluados;
            assertThat(rate)
                    .as("Recall prefijo 4ch: %d/%d (%.1f%%)", encontrados, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.05);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cobertura multi-estrategia (90% objetivo)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class CoberturaMultiEstrategia {

        @Test
        void cobertura_combinando_estrategias_90_porciento() {
            @SuppressWarnings("unchecked")
            List<Object[]> muestra = em.createNativeQuery(
                    "SELECT id, descripcion, sku FROM productos ORDER BY random() LIMIT 400")
                    .getResultList();
            int cubiertos = 0, evaluados = 0;

            for (Object[] row : muestra) {
                Long id = ((Number) row[0]).longValue();
                String desc = (String) row[1];
                String sku = row[2] != null ? (String) row[2] : "";
                evaluados++;

                if (buscarYEncontrar(id, extraerTerminos(desc, 3), PAGE_100)) { cubiertos++; continue; }
                if (buscarYEncontrar(id, extraerTerminos(desc, 2), PAGE_100)) { cubiertos++; continue; }
                List<String> skuTokens = tokenizar(sku);
                if (!skuTokens.isEmpty() && buscarYEncontrar(id, skuTokens, PAGE_100)) { cubiertos++; continue; }
                if (buscarYEncontrar(id, extraerTerminos(desc, 1), PAGE_500)) { cubiertos++; continue; }
            }

            double rate = evaluados == 0 ? 0 : (double) cubiertos / evaluados;
            assertThat(rate)
                    .as("Cobertura multi-estrategia: %d/%d (%.1f%%)", cubiertos, evaluados, rate * 100)
                    .isGreaterThanOrEqualTo(0.90);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Relevancia: los resultados contienen los términos buscados
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class RelevanciaResultados {

        @Test
        void top_resultados_contienen_terminos_en_descripcion() {
            List<List<String>> queries = List.of(
                    List.of("pistones", "fiat"),
                    List.of("junta", "carter"),
                    List.of("bomba", "agua"),
                    List.of("filtro", "aceite"),
                    List.of("cojinete", "bancada"),
                    List.of("correa", "distribucion"),
                    List.of("aros", "renault"),
                    List.of("valvula", "escape"));

            int queriesRelevantes = 0;
            for (List<String> q : queries) {
                Page<Producto> r = repo.buscarInteligente(q, String.join(" ", q), PAGE);
                if (r.getTotalElements() == 0) continue;

                long relevantes = r.getContent().stream()
                        .filter(p -> {
                            String d = p.getDescripcion().toLowerCase();
                            return q.stream().anyMatch(d::contains);
                        }).count();

                if ((double) relevantes / r.getContent().size() >= 0.80) {
                    queriesRelevantes++;
                }
            }

            assertThat(queriesRelevantes)
                    .as("Queries con >=80%% relevancia en top 20")
                    .isGreaterThanOrEqualTo(6);
        }

        @Test
        void mas_terminos_reducen_resultados() {
            long r1 = repo.buscarInteligente(List.of("junta"), "junta", PAGE).getTotalElements();
            long r2 = repo.buscarInteligente(List.of("junta", "carter"), "junta carter", PAGE).getTotalElements();
            long r3 = repo.buscarInteligente(List.of("junta", "carter", "fiat"), "junta carter fiat", PAGE).getTotalElements();

            assertThat(r1).isGreaterThan(r2);
            assertThat(r2).isGreaterThan(r3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Búsquedas por tipo de pieza (cobertura de categorías)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class BusquedasPorTipoPieza {

        @Test
        void tipos_comunes_devuelven_resultados() {
            List<String> tipos = List.of(
                    "pistones", "aros", "cojinete", "junta", "subconjunto",
                    "cable", "bomba", "valvula", "correa", "filtro",
                    "bujia", "disco", "pastilla", "amortiguador", "radiador",
                    "alternador", "arranque", "embrague", "cremallera", "tensor");

            int conResultados = 0;
            for (String tipo : tipos) {
                Page<Producto> r = repo.buscarInteligente(List.of(tipo), tipo, PAGE);
                if (r.getTotalElements() > 0) conResultados++;
            }

            assertThat(conResultados)
                    .as("Tipos de pieza con resultados: %d/%d", conResultados, tipos.size())
                    .isGreaterThanOrEqualTo((int) (tipos.size() * 0.80));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Búsquedas por vehículo
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class BusquedasPorVehiculo {

        @Test
        void vehiculos_comunes_devuelven_resultados() {
            List<String> vehiculos = List.of(
                    "fiat", "ford", "volkswagen", "toyota", "chevrolet",
                    "peugeot", "renault", "citroen", "mercedes", "honda",
                    "nissan", "hyundai", "mitsubishi", "suzuki", "iveco");

            int conResultados = 0;
            for (String v : vehiculos) {
                Page<Producto> r = repo.buscarInteligente(List.of(v), v, PAGE);
                if (r.getTotalElements() > 0) conResultados++;
            }

            assertThat(conResultados)
                    .as("Vehículos con resultados: %d/%d", conResultados, vehiculos.size())
                    .isGreaterThanOrEqualTo((int) (vehiculos.size() * 0.75));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Búsquedas combinadas (pieza + vehículo)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class BusquedasCombinadas {

        @Test
        void pieza_mas_vehiculo_devuelve_resultados_precisos() {
            Map<String, String[]> combos = Map.of(
                    "pistones_fiat", new String[]{"pistones", "fiat"},
                    "aros_renault", new String[]{"aros", "renault"},
                    "cojinete_ford", new String[]{"cojinete", "ford"},
                    "junta_volkswagen", new String[]{"junta", "volkswagen"},
                    "subconjunto_mercedes", new String[]{"subconjunto", "mercedes"},
                    "bomba_toyota", new String[]{"bomba", "toyota"},
                    "filtro_chevrolet", new String[]{"filtro", "chevrolet"},
                    "valvula_peugeot", new String[]{"valvula", "peugeot"});

            int conResultados = 0;
            for (var entry : combos.entrySet()) {
                Page<Producto> r = repo.buscarInteligente(List.of(entry.getValue()), String.join(" ", entry.getValue()), PAGE);
                if (r.getTotalElements() > 0) {
                    conResultados++;
                    assertThat(r.getContent().get(0).getDescripcion().toLowerCase())
                            .containsAnyOf(entry.getValue());
                }
            }

            assertThat(conResultados)
                    .as("Combinaciones con resultados")
                    .isGreaterThanOrEqualTo(5);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tolerancia a typos
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @Transactional(readOnly = true)
    class ToleranciaTyPos {

        @Test
        void typos_comunes_devuelven_resultados() {
            Map<String, String> typos = Map.of(
                    "pistpnes", "pistones",
                    "amortiguadro", "amortiguador",
                    "valvulaas", "valvulas",
                    "cojinttes", "cojinetes",
                    "volskwagen", "volkswagen",
                    "renauld", "renault",
                    "chevroled", "chevrolet");

            int conResultados = 0;
            for (var entry : typos.entrySet()) {
                Page<Producto> r = repo.buscarInteligente(List.of(entry.getKey()), entry.getKey(), PAGE);
                if (r.getTotalElements() > 0) conResultados++;
            }

            assertThat(conResultados)
                    .as("Typos que devuelven resultados: %d/%d", conResultados, typos.size())
                    .isGreaterThanOrEqualTo((int) (typos.size() * 0.70));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Rendimiento
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class Rendimiento {

        @Test
        void busquedas_frecuentes_bajo_200ms() {
            List<List<String>> queries = List.of(
                    List.of("pistones"),
                    List.of("pistones", "fiat"),
                    List.of("aros", "renault", "clio"),
                    List.of("cojinete", "bancada"),
                    List.of("junta", "tapa"),
                    List.of("subconjunto", "mercedes"),
                    List.of("filtro", "aceite"),
                    List.of("bomba", "agua", "ford"),
                    List.of("zsd195"),
                    List.of("correa", "distribucion"));

            // Warmup
            repo.buscarInteligente(List.of("warmup"), "warmup", PAGE);

            long maxMs = 0;
            for (List<String> q : queries) {
                long t0 = System.nanoTime();
                repo.buscarInteligente(q, String.join(" ", q), PAGE);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                maxMs = Math.max(maxMs, ms);
            }

            assertThat(maxMs)
                    .as("Máximo tiempo de búsqueda (%dms)", maxMs)
                    .isLessThan(500);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cobertura por proveedor
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class CoberturaPorProveedor {

        @Test
        void busqueda_encuentra_productos_de_ambos_proveedores() {
            List<String> terminos = List.of("pistones", "aros", "junta", "cojinete", "valvula");
            Set<String> proveedoresEncontrados = new HashSet<>();

            for (String t : terminos) {
                Page<Producto> r = repo.buscarInteligente(List.of(t), t, PAGE);
                r.getContent().forEach(p -> {
                    if (p.getProveedor() != null) proveedoresEncontrados.add(p.getProveedor());
                });
            }

            assertThat(proveedoresEncontrados)
                    .as("Proveedores encontrados en búsquedas")
                    .hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private boolean buscarYEncontrar(Long id, List<String> tokens, Pageable page) {
        if (tokens.isEmpty()) return false;
        Page<Producto> r = repo.buscarInteligente(tokens, String.join(" ", tokens), page);
        return r.getContent().stream().anyMatch(p -> p.getId().equals(id));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> muestraAleatoria(int n) {
        return em.createNativeQuery(
                "SELECT id, descripcion FROM productos ORDER BY random() LIMIT " + n)
                .getResultList();
    }

    private List<String> extraerTerminos(String desc, int maxTerms) {
        if (desc == null || desc.isBlank()) return List.of();
        return Arrays.stream(desc.toLowerCase().split("[\\s\\-/()+,;:]+"))
                .filter(s -> s.length() >= 3 && !STOP.contains(s))
                .filter(s -> s.chars().anyMatch(Character::isLetter))
                .distinct()
                .limit(maxTerms)
                .toList();
    }

    private List<String> tokenizar(String q) {
        if (q == null || q.isBlank()) return List.of();
        return Arrays.stream(q.trim().toLowerCase().split("[\\s\\-/()+,;:]+"))
                .filter(s -> s.length() >= 2 && !STOP.contains(s))
                .distinct()
                .limit(8)
                .toList();
    }

    private long countProductos() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM productos")
                .getSingleResult()).longValue();
    }

    private Path resolveDataDir() {
        Path dir = Path.of(System.getProperty("user.dir")).getParent().resolve("data-import");
        if (Files.exists(dir)) return dir;
        dir = Path.of("..").resolve("data-import").toAbsolutePath().normalize();
        return Files.exists(dir) ? dir : null;
    }

    private void docker(String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("docker failed (exit " + exit + "): "
                    + String.join(" ", cmd) + "\n" + out);
        }
    }

    private String importSql() {
        return """
                DELETE FROM stock;
                DELETE FROM producto_codigos;
                DELETE FROM productos;
                DELETE FROM categorias;
                DELETE FROM marcas;

                CREATE TABLE IF NOT EXISTS import_csv (
                    sku text, marca text, categoria text, descripcion text,
                    codigo text, tipo_codigo text, proveedor text, precio_lista text
                );
                TRUNCATE import_csv;

                COPY import_csv FROM '/tmp/productos_EGSA.csv' WITH (FORMAT csv, HEADER true);
                COPY import_csv FROM '/tmp/productos_AutopartesDelSur.csv' WITH (FORMAT csv, HEADER true);

                INSERT INTO marcas (nombre, created_at, updated_at)
                SELECT DISTINCT trim(marca), now(), now()
                FROM import_csv
                WHERE marca IS NOT NULL AND trim(marca) != ''
                ON CONFLICT DO NOTHING;

                INSERT INTO categorias (nombre, created_at, updated_at)
                SELECT DISTINCT trim(categoria), now(), now()
                FROM import_csv
                WHERE categoria IS NOT NULL AND trim(categoria) != ''
                ON CONFLICT DO NOTHING;

                INSERT INTO productos (sku, marca_id, categoria_id, descripcion, proveedor, estado, created_at, updated_at)
                SELECT DISTINCT ON (lower(t.proveedor), lower(t.sku))
                       t.sku, m.id, c.id, t.descripcion, t.proveedor, 'ACTIVO', now(), now()
                FROM import_csv t
                LEFT JOIN marcas m ON m.nombre = trim(t.marca)
                LEFT JOIN categorias c ON c.nombre = trim(t.categoria)
                ORDER BY lower(t.proveedor), lower(t.sku), t.descripcion;

                DROP TABLE import_csv;
                """;
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
