package com.partvision.ai.search;

import java.util.List;

public record SearchInterpretation(
        String originalQuery,
        String normalizedQuery,
        List<String> terms,
        List<String> synonyms,
        SearchIntent intent,
        SearchFilters filters,
        double confidence
) {
    public SearchInterpretation {
        if (terms == null) terms = List.of();
        if (synonyms == null) synonyms = List.of();
        if (intent == null) intent = SearchIntent.UNKNOWN;
        if (filters == null) filters = SearchFilters.EMPTY;
    }
}
