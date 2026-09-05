package com.partvision.ai.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SearchOrchestrator.class);

    private final AiSearchProperties properties;
    private final SearchQueryClassifier classifier;
    private final SearchInterpreter interpreter;
    private final SearchInterpretationCache cache;

    public SearchOrchestrator(
            AiSearchProperties properties,
            SearchQueryClassifier classifier,
            ObjectProvider<SearchInterpreter> interpreterProvider,
            ObjectProvider<SearchInterpretationCache> cacheProvider) {
        this.properties = properties;
        this.classifier = classifier;
        this.interpreter = interpreterProvider.getIfAvailable();
        this.cache = cacheProvider.getIfAvailable();
    }

    public ProcessedQuery processQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return ProcessedQuery.passthrough(originalQuery);
        }
        if (!properties.enabled() || interpreter == null) {
            return ProcessedQuery.passthrough(originalQuery);
        }

        if (!classifier.shouldUseAi(originalQuery)) {
            log.debug("AI_SEARCH_BYPASSED: query too simple [{}]", originalQuery);
            return ProcessedQuery.passthrough(originalQuery);
        }

        String cacheKey = normalizeForCache(originalQuery);

        if (cache != null) {
            Optional<SearchInterpretation> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                log.info("AI_SEARCH_CACHE_HIT: [{}]", originalQuery);
                return toProcessedQuery(cached.get());
            }
        }

        log.info("AI_SEARCH_USED: [{}]", originalQuery);
        Optional<SearchInterpretation> interpretation = interpreter.interpret(originalQuery);

        if (interpretation.isEmpty()) {
            log.info("AI_SEARCH_FALLBACK: interpreter returned empty for [{}]", originalQuery);
            return ProcessedQuery.passthrough(originalQuery);
        }

        SearchInterpretation interp = interpretation.get();

        if (interp.confidence() < properties.minConfidence()) {
            log.info("AI_SEARCH_LOW_CONFIDENCE: {} < {} for [{}]",
                    interp.confidence(), properties.minConfidence(), originalQuery);
            return ProcessedQuery.passthrough(originalQuery);
        }

        if (cache != null) {
            cache.put(cacheKey, interp);
        }

        ProcessedQuery result = toProcessedQuery(interp);
        log.info("AI_SEARCH_INTERPRETED: [{}] -> [{}] synonyms={} (confidence={})",
                originalQuery, result.query(), result.synonyms(), interp.confidence());
        return result;
    }

    private ProcessedQuery toProcessedQuery(SearchInterpretation interpretation) {
        String query = String.join(" ", interpretation.terms());
        List<String> synonyms = interpretation.synonyms() == null
                ? List.of() : interpretation.synonyms();
        return new ProcessedQuery(query, synonyms, true);
    }

    static String normalizeForCache(String query) {
        return Arrays.stream(query.strip().toLowerCase().split("\\s+"))
                .filter(t -> t.length() >= 2)
                .sorted()
                .distinct()
                .collect(Collectors.joining(" "));
    }
}
