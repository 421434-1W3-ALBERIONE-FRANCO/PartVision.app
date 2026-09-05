package com.partvision.ai.search;

public record SearchFilters(
        String brand,
        String category,
        Boolean inStock
) {
    public static final SearchFilters EMPTY = new SearchFilters(null, null, null);
}
