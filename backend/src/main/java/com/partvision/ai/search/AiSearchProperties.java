package com.partvision.ai.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "partvision.search.ai")
public record AiSearchProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("gemini") String provider,
        @DefaultValue("gemini-flash-latest") String model,
        @DefaultValue("1000") long timeoutMs,
        @DefaultValue("0.70") double minConfidence,
        @DefaultValue("720") long cacheTtlMinutes
) {}
