package com.partvision.ai.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los caminos por los que la busqueda decide NO usar la IA, y el cache.
 * Cualquiera de ellos tiene que terminar en una busqueda normal, nunca en un error.
 */
class SearchOrchestratorBordesTest {

    private final SearchInterpreter interpreter = mock(SearchInterpreter.class);
    private final SearchInterpretationCache cache = mock(SearchInterpretationCache.class);

    @SuppressWarnings("unchecked")
    private SearchOrchestrator orchestrator(boolean habilitada, double minConfidence,
                                            SearchInterpreter interp, SearchInterpretationCache cach) {
        ObjectProvider<SearchInterpreter> ip = mock(ObjectProvider.class);
        ObjectProvider<SearchInterpretationCache> cp = mock(ObjectProvider.class);
        when(ip.getIfAvailable()).thenReturn(interp);
        when(cp.getIfAvailable()).thenReturn(cach);
        var props = new AiSearchProperties(habilitada, "gemini", "gemini-flash-latest", 1000, minConfidence, 720);
        return new SearchOrchestrator(props, new SearchQueryClassifier(), ip, cp);
    }

    private static SearchInterpretation interpretacion(double confianza) {
        return new SearchInterpretation("pastillas de freno para gol trend",
                "pastillas freno gol trend", List.of("pastillas", "freno"), List.of("balatas"),
                SearchIntent.PRODUCT_SEARCH, SearchFilters.EMPTY, confianza);
    }

    private static final String CONSULTA = "necesito pastillas de freno para un gol trend";

    @Test
    void consultaNula_pasaDerecho() {
        assertThat(orchestrator(true, 0.7, interpreter, cache).processQuery(null).aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void consultaEnBlanco_pasaDerecho() {
        assertThat(orchestrator(true, 0.7, interpreter, cache).processQuery("  ").aiInterpreted()).isFalse();
    }

    @Test
    void iaDeshabilitada_pasaDerecho() {
        assertThat(orchestrator(false, 0.7, interpreter, cache).processQuery(CONSULTA).aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void sinInterpreteDisponible_pasaDerecho() {
        assertThat(orchestrator(true, 0.7, null, cache).processQuery(CONSULTA).aiInterpreted()).isFalse();
    }

    /** Una consulta corta no justifica el costo ni la latencia de la IA. */
    @Test
    void consultaDemasiadoSimple_pasaDerecho() {
        assertThat(orchestrator(true, 0.7, interpreter, cache).processQuery("gol").aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void respuestaCacheada_noVuelveALlamarALaIa() {
        when(cache.get(anyString())).thenReturn(Optional.of(interpretacion(0.9)));

        ProcessedQuery pq = orchestrator(true, 0.7, interpreter, cache).processQuery(CONSULTA);

        assertThat(pq.aiInterpreted()).isTrue();
        assertThat(pq.query()).isEqualTo("pastillas freno");
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void interpretacionVacia_pasaDerecho() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(CONSULTA)).thenReturn(Optional.empty());

        assertThat(orchestrator(true, 0.7, interpreter, cache).processQuery(CONSULTA).aiInterpreted()).isFalse();
        verify(cache, never()).put(anyString(), any());
    }

    /** Con poca confianza no se pisa la consulta del usuario ni se cachea. */
    @Test
    void confianzaBaja_pasaDerechoYNoCachea() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(CONSULTA)).thenReturn(Optional.of(interpretacion(0.3)));

        assertThat(orchestrator(true, 0.7, interpreter, cache).processQuery(CONSULTA).aiInterpreted()).isFalse();
        verify(cache, never()).put(anyString(), any());
    }

    @Test
    void confianzaSuficiente_interpretaYCachea() {
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(CONSULTA)).thenReturn(Optional.of(interpretacion(0.9)));

        ProcessedQuery pq = orchestrator(true, 0.7, interpreter, cache).processQuery(CONSULTA);

        assertThat(pq.aiInterpreted()).isTrue();
        assertThat(pq.synonyms()).containsExactly("balatas");
        verify(cache).put(anyString(), any());
    }

    @Test
    void sinCache_funcionaIgual() {
        when(interpreter.interpret(CONSULTA)).thenReturn(Optional.of(interpretacion(0.9)));

        assertThat(orchestrator(true, 0.7, interpreter, null).processQuery(CONSULTA).aiInterpreted()).isTrue();
    }

    /** La clave del cache ignora orden, mayusculas y palabras de una letra. */
    @Test
    void normalizeForCache_ordenaYDeduplica() {
        assertThat(SearchOrchestrator.normalizeForCache("  Gol TREND gol a 12 "))
                .isEqualTo("12 gol trend");
    }
}
