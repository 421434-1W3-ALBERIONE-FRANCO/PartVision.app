package com.partvision.ai.search;

import java.util.List;

public record ProcessedQuery(
        String query,
        List<String> synonyms,
        boolean aiInterpreted
) {
    public static ProcessedQuery passthrough(String originalQuery) {
        return new ProcessedQuery(originalQuery, List.of(), false);
    }
}
