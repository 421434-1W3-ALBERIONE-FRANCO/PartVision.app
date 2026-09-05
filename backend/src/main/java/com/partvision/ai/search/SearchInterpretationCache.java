package com.partvision.ai.search;

import java.util.Optional;

public interface SearchInterpretationCache {

    Optional<SearchInterpretation> get(String normalizedQuery);

    void put(String normalizedQuery, SearchInterpretation interpretation);
}
