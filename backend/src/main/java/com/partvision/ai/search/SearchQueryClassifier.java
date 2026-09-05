package com.partvision.ai.search;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
public class SearchQueryClassifier {

    private static final Set<String> NATURAL_LANGUAGE_SIGNALS = Set.of(
            "necesito", "busco", "quiero", "tengo", "tenes", "tienen",
            "para", "compatible", "sirve", "motor", "modelo", "año", "anio",
            "delantero", "trasero", "izquierdo", "derecho",
            "superior", "inferior", "interno", "externo",
            "algo", "tipo", "como", "cual", "donde",
            "cambiar", "reparar", "reemplazar"
    );

    public boolean shouldUseAi(String query) {
        if (query == null || query.isBlank()) return false;

        List<String> tokens = Arrays.stream(query.strip().toLowerCase().split("[\\s\\-/()+,;:]+"))
                .filter(t -> t.length() >= 2)
                .toList();

        if (tokens.size() <= 2) return false;
        if (tokens.size() >= 4) return true;

        // 3 tokens: solo si hay señal de lenguaje natural
        return tokens.stream().anyMatch(NATURAL_LANGUAGE_SIGNALS::contains);
    }
}
