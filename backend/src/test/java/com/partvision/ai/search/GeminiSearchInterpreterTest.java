package com.partvision.ai.search;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiSearchInterpreterTest {

    @Test
    void parseAndValidate_validJson() {
        var interpreter = createInterpreter();
        String json = """
                {
                  "normalizedQuery": "pastillas freno gol trend 2012",
                  "terms": ["pastillas", "freno", "gol", "trend", "2012"],
                  "synonyms": ["pastilla de freno"],
                  "intent": "PRODUCT_SEARCH",
                  "filters": {"brand": null, "category": null},
                  "confidence": 0.91
                }""";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("necesito pastillas para gol", json);

        assertThat(result).isPresent();
        SearchInterpretation interp = result.get();
        assertThat(interp.originalQuery()).isEqualTo("necesito pastillas para gol");
        assertThat(interp.normalizedQuery()).isEqualTo("pastillas freno gol trend 2012");
        assertThat(interp.terms()).containsExactly("pastillas", "freno", "gol", "trend", "2012");
        assertThat(interp.synonyms()).containsExactly("pastilla de freno");
        assertThat(interp.intent()).isEqualTo(SearchIntent.PRODUCT_SEARCH);
        assertThat(interp.confidence()).isEqualTo(0.91);
    }

    @Test
    void parseAndValidate_invalidJson_returnEmpty() {
        var interpreter = createInterpreter();
        String text = "te recomiendo buscar pastillas de freno en la sección de frenos";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("pastillas", text);

        assertThat(result).isEmpty();
    }

    @Test
    void parseAndValidate_emptyText_returnEmpty() {
        var interpreter = createInterpreter();

        assertThat(interpreter.parseAndValidate("q", null)).isEmpty();
        assertThat(interpreter.parseAndValidate("q", "")).isEmpty();
        assertThat(interpreter.parseAndValidate("q", "  ")).isEmpty();
    }

    @Test
    void parseAndValidate_missingNormalizedQuery_returnEmpty() {
        var interpreter = createInterpreter();
        String json = """
                {"terms": ["pistones"], "confidence": 0.9}""";

        assertThat(interpreter.parseAndValidate("pistones", json)).isEmpty();
    }

    @Test
    void parseAndValidate_emptyTerms_returnEmpty() {
        var interpreter = createInterpreter();
        String json = """
                {"normalizedQuery": "pistones", "terms": [], "confidence": 0.9}""";

        assertThat(interpreter.parseAndValidate("pistones", json)).isEmpty();
    }

    @Test
    void parseAndValidate_confidenceClamped() {
        var interpreter = createInterpreter();
        String json = """
                {
                  "normalizedQuery": "test",
                  "terms": ["test"],
                  "confidence": 1.5
                }""";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("test", json);

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isEqualTo(1.0);
    }

    @Test
    void parseAndValidate_unknownIntent_defaultsToUnknown() {
        var interpreter = createInterpreter();
        String json = """
                {
                  "normalizedQuery": "test",
                  "terms": ["test"],
                  "intent": "INVENTED_INTENT",
                  "confidence": 0.8
                }""";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("test", json);

        assertThat(result).isPresent();
        assertThat(result.get().intent()).isEqualTo(SearchIntent.UNKNOWN);
    }

    @Test
    void parseAndValidate_termsLimitedAndTrimmed() {
        var interpreter = createInterpreter();
        StringBuilder terms = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            if (i > 0) terms.append(",");
            terms.append("\"term").append(i).append("\"");
        }
        terms.append("]");
        String json = "{\"normalizedQuery\": \"test\", \"terms\": " + terms + ", \"confidence\": 0.8}";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("test", json);

        assertThat(result).isPresent();
        assertThat(result.get().terms()).hasSize(10);
    }

    @Test
    void parseAndValidate_synonymsLimitedToThree() {
        var interpreter = createInterpreter();
        String json = """
                {
                  "normalizedQuery": "test",
                  "terms": ["test"],
                  "synonyms": ["syn1", "syn2", "syn3", "syn4", "syn5"],
                  "confidence": 0.8
                }""";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("test", json);

        assertThat(result).isPresent();
        assertThat(result.get().synonyms()).hasSize(3);
    }

    @Test
    void parseAndValidate_filtersExtracted() {
        var interpreter = createInterpreter();
        String json = """
                {
                  "normalizedQuery": "pistones mahle",
                  "terms": ["pistones"],
                  "filters": {"brand": "Mahle", "category": "Motor"},
                  "confidence": 0.85
                }""";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("pistones mahle", json);

        assertThat(result).isPresent();
        assertThat(result.get().filters().brand()).isEqualTo("Mahle");
        assertThat(result.get().filters().category()).isEqualTo("Motor");
        assertThat(result.get().filters().inStock()).isNull();
    }

    @Test
    void parseAndValidate_jsonWrappedInText() {
        var interpreter = createInterpreter();
        String text = "Here is the result: {\"normalizedQuery\": \"test\", \"terms\": [\"test\"], \"confidence\": 0.8} end.";

        Optional<SearchInterpretation> result = interpreter.parseAndValidate("test", text);

        assertThat(result).isPresent();
        assertThat(result.get().normalizedQuery()).isEqualTo("test");
    }

    private GeminiSearchInterpreter createInterpreter() {
        // RestClient and API key not needed for parse-only tests
        return new GeminiSearchInterpreter(null, "", new AiSearchProperties(
                false, "gemini", "gemini-flash-latest", 1000, 0.70, 720));
    }
}
