package com.partvision.ai.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryClassifierTest {

    private final SearchQueryClassifier classifier = new SearchQueryClassifier();

    @ParameterizedTest
    @ValueSource(strings = {"6204", "SKF1234", "mah3002", "aros", "mahle", "embrague"})
    void singleToken_shouldNotUseAi(String query) {
        assertThat(classifier.shouldUseAi(query)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pastillas freno", "aros mahle", "filtro aceite"})
    void twoTokens_shouldNotUseAi(String query) {
        assertThat(classifier.shouldUseAi(query)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aros mahle volkswagen", "filtro aceite gol"})
    void threeTokens_noSignal_shouldNotUseAi(String query) {
        assertThat(classifier.shouldUseAi(query)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"filtro para gol", "busco ruleman corsa"})
    void threeTokens_withSignal_shouldUseAi(String query) {
        assertThat(classifier.shouldUseAi(query)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pastillas freno gol trend 2012",
            "necesito pastillas de freno para gol trend 2012",
            "busco ruleman delantero para corsa",
            "kit distribucion gol 1.6",
            "algo para cambiar el ruleman delantero del corsa"
    })
    void fourOrMoreTokens_shouldUseAi(String query) {
        assertThat(classifier.shouldUseAi(query)).isTrue();
    }

    @Test
    void nullQuery_shouldNotUseAi() {
        assertThat(classifier.shouldUseAi(null)).isFalse();
    }

    @Test
    void emptyQuery_shouldNotUseAi() {
        assertThat(classifier.shouldUseAi("")).isFalse();
        assertThat(classifier.shouldUseAi("   ")).isFalse();
    }

    @Test
    void shortTokensFiltered() {
        // "a b c d e" -> all tokens < 2 chars -> empty -> false
        assertThat(classifier.shouldUseAi("a b c d e")).isFalse();
    }
}
