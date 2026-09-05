package com.partvision.ai.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "partvision.search.ai.enabled", havingValue = "true")
public class InMemorySearchInterpretationCache implements SearchInterpretationCache {

    private static final int MAX_ENTRIES = 5_000;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;

    public InMemorySearchInterpretationCache(AiSearchProperties properties) {
        this.ttlMs = properties.cacheTtlMinutes() * 60 * 1000;
    }

    @Override
    public Optional<SearchInterpretation> get(String normalizedQuery) {
        CacheEntry entry = cache.get(normalizedQuery);
        if (entry == null) return Optional.empty();
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(normalizedQuery);
            return Optional.empty();
        }
        return Optional.of(entry.interpretation);
    }

    @Override
    public void put(String normalizedQuery, SearchInterpretation interpretation) {
        if (cache.size() >= MAX_ENTRIES) {
            evictExpired();
        }
        cache.put(normalizedQuery, new CacheEntry(interpretation, System.currentTimeMillis()));
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > ttlMs);
    }

    private record CacheEntry(SearchInterpretation interpretation, long timestamp) {}
}
