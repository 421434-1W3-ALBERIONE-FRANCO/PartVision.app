package com.partvision.ai.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchOrchestratorTest {

    @Mock SearchInterpreter interpreter;
    @Mock SearchInterpretationCache cache;

    private SearchOrchestrator create(boolean enabled, double minConfidence) {
        var props = new AiSearchProperties(enabled, "gemini", "gemini-flash-latest",
                1000, minConfidence, 720);
        var classifier = new SearchQueryClassifier();
        @SuppressWarnings("unchecked")
        ObjectProvider<SearchInterpreter> ip = org.mockito.Mockito.mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SearchInterpretationCache> cp = org.mockito.Mockito.mock(ObjectProvider.class);
        when(ip.getIfAvailable()).thenReturn(interpreter);
        when(cp.getIfAvailable()).thenReturn(cache);
        return new SearchOrchestrator(props, classifier, ip, cp);
    }

    @Test
    void aiDisabled_returnsOriginalQuery() {
        SearchOrchestrator orch = create(false, 0.70);

        ProcessedQuery result = orch.processQuery("necesito pastillas de freno para gol trend 2012");

        assertThat(result.query()).isEqualTo("necesito pastillas de freno para gol trend 2012");
        assertThat(result.aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void simpleQuery_skipsAi() {
        SearchOrchestrator orch = create(true, 0.70);

        ProcessedQuery result = orch.processQuery("6204");

        assertThat(result.query()).isEqualTo("6204");
        assertThat(result.aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void twoWordQuery_skipsAi() {
        SearchOrchestrator orch = create(true, 0.70);

        ProcessedQuery result = orch.processQuery("aros mahle");

        assertThat(result.query()).isEqualTo("aros mahle");
        assertThat(result.aiInterpreted()).isFalse();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void complexQuery_callsGemini() {
        SearchOrchestrator orch = create(true, 0.70);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(anyString())).thenReturn(Optional.of(
                new SearchInterpretation(
                        "necesito pastillas de freno para gol trend 2012",
                        "pastillas freno gol trend 2012",
                        List.of("pastillas", "freno", "gol", "trend", "2012"),
                        List.of(), SearchIntent.PRODUCT_SEARCH, SearchFilters.EMPTY, 0.91
                )
        ));

        ProcessedQuery result = orch.processQuery("necesito pastillas de freno para gol trend 2012");

        assertThat(result.query()).isEqualTo("pastillas freno gol trend 2012");
        assertThat(result.aiInterpreted()).isTrue();
        assertThat(result.synonyms()).isEmpty();
        verify(interpreter).interpret("necesito pastillas de freno para gol trend 2012");
    }

    @Test
    void cacheHit_skipsGemini() {
        SearchOrchestrator orch = create(true, 0.70);
        SearchInterpretation cached = new SearchInterpretation(
                "original", "pastillas freno",
                List.of("pastillas", "freno"), List.of(),
                SearchIntent.PRODUCT_SEARCH, SearchFilters.EMPTY, 0.90
        );
        when(cache.get(anyString())).thenReturn(Optional.of(cached));

        ProcessedQuery result = orch.processQuery("necesito pastillas de freno para gol trend 2012");

        assertThat(result.query()).isEqualTo("pastillas freno");
        assertThat(result.aiInterpreted()).isTrue();
        verify(interpreter, never()).interpret(anyString());
    }

    @Test
    void geminiTimeout_fallsBackToOriginal() {
        SearchOrchestrator orch = create(true, 0.70);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(anyString())).thenReturn(Optional.empty());

        ProcessedQuery result = orch.processQuery("necesito pastillas de freno para gol trend 2012");

        assertThat(result.query()).isEqualTo("necesito pastillas de freno para gol trend 2012");
        assertThat(result.aiInterpreted()).isFalse();
    }

    @Test
    void lowConfidence_fallsBackToOriginal() {
        SearchOrchestrator orch = create(true, 0.70);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(anyString())).thenReturn(Optional.of(
                new SearchInterpretation(
                        "original", "algo",
                        List.of("algo"), List.of(),
                        SearchIntent.UNKNOWN, SearchFilters.EMPTY, 0.30
                )
        ));

        ProcessedQuery result = orch.processQuery("necesito pastillas de freno para gol trend 2012");

        assertThat(result.query()).isEqualTo("necesito pastillas de freno para gol trend 2012");
        assertThat(result.aiInterpreted()).isFalse();
    }

    @Test
    void synonyms_separatedFromTerms() {
        SearchOrchestrator orch = create(true, 0.70);
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(interpreter.interpret(anyString())).thenReturn(Optional.of(
                new SearchInterpretation(
                        "original", "ruleman delantero corsa",
                        List.of("ruleman", "delantero", "corsa"),
                        List.of("rodamiento"),
                        SearchIntent.PRODUCT_SEARCH, SearchFilters.EMPTY, 0.85
                )
        ));

        ProcessedQuery result = orch.processQuery("busco ruleman delantero para corsa");

        assertThat(result.query()).isEqualTo("ruleman delantero corsa");
        assertThat(result.synonyms()).containsExactly("rodamiento");
        assertThat(result.aiInterpreted()).isTrue();
    }

    @Test
    void nullQuery_returnedAsPassthrough() {
        SearchOrchestrator orch = create(true, 0.70);

        assertThat(orch.processQuery(null).query()).isNull();
        assertThat(orch.processQuery(null).aiInterpreted()).isFalse();
        assertThat(orch.processQuery("").query()).isEmpty();
    }

    @Test
    void normalizeForCache_sortsDedupesLowers() {
        assertThat(SearchOrchestrator.normalizeForCache("Necesito PASTILLAS para GOL"))
                .isEqualTo("gol necesito para pastillas");
    }
}
