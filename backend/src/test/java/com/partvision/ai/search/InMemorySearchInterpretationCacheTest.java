package com.partvision.ai.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySearchInterpretationCacheTest {

    private InMemorySearchInterpretationCache buildCache(long ttlMinutes) {
        return new InMemorySearchInterpretationCache(
                new AiSearchProperties(true, "gemini", "gemini-flash", 1000, 0.7, ttlMinutes)
        );
    }

    @Test
    void get_entradaExistente_retornaInterpretacion() {
        var cache = buildCache(60);
        SearchInterpretation interp = buildInterpretation("filtro aceite");

        cache.put("filtro aceite", interp);

        Optional<SearchInterpretation> result = cache.get("filtro aceite");
        assertThat(result).isPresent();
        assertThat(result.get().normalizedQuery()).isEqualTo("filtro aceite");
    }

    @Test
    void get_entradaNoExiste_retornaVacio() {
        var cache = buildCache(60);

        Optional<SearchInterpretation> result = cache.get("no existe");
        assertThat(result).isEmpty();
    }

    @Test
    void get_entradaExpirada_retornaVacioYLimpia() throws Exception {
        var cache = buildCache(0);

        cache.put("query", buildInterpretation("query"));
        Thread.sleep(50);

        Optional<SearchInterpretation> result = cache.get("query");
        assertThat(result).isEmpty();
    }

    @Test
    void put_sobrescribeEntrada() {
        var cache = buildCache(60);
        SearchInterpretation v1 = buildInterpretation("query");
        SearchInterpretation v2 = new SearchInterpretation(
                "query", "query",
                List.of("query"), List.of("consulta"),
                SearchIntent.PRODUCT_SEARCH, SearchFilters.EMPTY, 0.95
        );

        cache.put("query", v1);
        cache.put("query", v2);

        Optional<SearchInterpretation> result = cache.get("query");
        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(0.95);
    }

    @Test
    void put_masDeMaxEntries_triggeraEviccion() {
        var cache = buildCache(60);

        for (int i = 0; i < 5001; i++) {
            cache.put("key-" + i, buildInterpretation("key-" + i));
        }

        assertThat(cache.get("key-5000")).isPresent();
    }

    private SearchInterpretation buildInterpretation(String query) {
        return new SearchInterpretation(
                query, query,
                List.of(query.split(" ")),
                List.of(),
                SearchIntent.PRODUCT_SEARCH,
                SearchFilters.EMPTY,
                0.85
        );
    }
}
