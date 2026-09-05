package com.partvision.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "partvision.search.ai.enabled", havingValue = "true")
public class GeminiSearchInterpreter implements SearchInterpreter {

    private static final Logger log = LoggerFactory.getLogger(GeminiSearchInterpreter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_TERMS = 10;
    private static final int MAX_SYNONYMS = 3;
    private static final int MAX_TERM_LENGTH = 50;

    static final String SYSTEM_PROMPT = """
            Sos un intérprete de consultas para un catálogo de repuestos automotores argentino.

            Tu función NO es buscar productos.
            Tu función NO es inventar productos.
            Tu función NO es responder al usuario.

            Tu única responsabilidad es transformar la consulta del usuario en una estructura JSON \
            que pueda usar un motor de búsqueda PostgreSQL.

            Reglas:
            - No inventes marcas, códigos SKU, modelos ni compatibilidades.
            - Conservá números, códigos y referencias técnicas tal cual.
            - No elimines términos que puedan formar parte de códigos de piezas.
            - Identificá sinónimos solamente cuando tengas alta confianza (máximo 3).
            - Si una palabra puede ser un código, conservarla literalmente.
            - Eliminá las palabras funcionales (necesito, busco, quiero, tenes, algo, etc.) \
              de los terms — solo dejá los sustantivos, marcas y códigos relevantes para buscar.

            Schema de respuesta (JSON estricto, sin texto adicional):
            {
              "normalizedQuery": "string - la consulta limpia sin palabras funcionales",
              "terms": ["array de términos relevantes para búsqueda"],
              "synonyms": ["sinónimos del rubro automotor, máximo 3"],
              "intent": "PRODUCT_SEARCH | SKU_SEARCH | BRAND_SEARCH | CATEGORY_SEARCH | UNKNOWN",
              "filters": {
                "brand": "string o null - marca del fabricante si se menciona explícitamente",
                "category": "string o null - categoría si se menciona explícitamente"
              },
              "confidence": 0.0-1.0
            }""";

    private final org.springframework.web.client.RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiSearchInterpreter(
            @Qualifier("aiSearchRestClient") org.springframework.web.client.RestClient restClient,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            AiSearchProperties properties) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = properties.model();
    }

    @Override
    public Optional<SearchInterpretation> interpret(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI_SEARCH_BYPASSED: GEMINI_API_KEY not configured");
            return Optional.empty();
        }

        try {
            Map<String, Object> body = buildRequestBody(query);

            JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = extractText(response);
            return parseAndValidate(query, text);

        } catch (Exception e) {
            log.warn("AI_SEARCH_TIMEOUT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    Map<String, Object> buildRequestBody(String query) {
        String prompt = SYSTEM_PROMPT + "\n\nConsulta del usuario: " + query;
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        return Map.of(
                "contents", List.of(content),
                "generationConfig", Map.of(
                        "maxOutputTokens", 1024,
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );
    }

    static String extractText(JsonNode response) {
        if (response == null) return null;
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && text.isValueNode()) {
                sb.append(text.asText());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    Optional<SearchInterpretation> parseAndValidate(String originalQuery, String text) {
        if (text == null || text.isBlank()) {
            log.warn("AI_SEARCH_INVALID_RESPONSE: empty text");
            return Optional.empty();
        }

        String json = extractJson(text);
        if (json == null) {
            log.warn("AI_SEARCH_INVALID_RESPONSE: no JSON found in response");
            return Optional.empty();
        }

        try {
            JsonNode node = MAPPER.readTree(json);
            return validate(originalQuery, node);
        } catch (Exception e) {
            log.warn("AI_SEARCH_INVALID_RESPONSE: JSON parse error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<SearchInterpretation> validate(String originalQuery, JsonNode node) {
        String normalizedQuery = textField(node, "normalizedQuery");
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            log.warn("AI_SEARCH_INVALID_RESPONSE: missing normalizedQuery");
            return Optional.empty();
        }

        List<String> terms = stringList(node.get("terms"), MAX_TERMS, MAX_TERM_LENGTH);
        if (terms.isEmpty()) {
            log.warn("AI_SEARCH_INVALID_RESPONSE: empty terms");
            return Optional.empty();
        }

        List<String> synonyms = stringList(node.get("synonyms"), MAX_SYNONYMS, MAX_TERM_LENGTH);
        double confidence = node.has("confidence") ? node.get("confidence").asDouble(0) : 0;
        confidence = Math.max(0, Math.min(1, confidence));

        SearchIntent intent = parseIntent(textField(node, "intent"));
        SearchFilters filters = parseFilters(node.get("filters"));

        return Optional.of(new SearchInterpretation(
                originalQuery, normalizedQuery, terms, synonyms,
                intent, filters, confidence
        ));
    }

    private static String extractJson(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }

    private static String textField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull() || !value.isValueNode()) ? null : value.asText();
    }

    private static List<String> stringList(JsonNode node, int maxItems, int maxLength) {
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isValueNode()) continue;
            String val = item.asText().strip();
            if (val.isEmpty() || val.length() > maxLength) continue;
            result.add(val);
            if (result.size() >= maxItems) break;
        }
        return List.copyOf(result);
    }

    private static SearchIntent parseIntent(String value) {
        if (value == null) return SearchIntent.UNKNOWN;
        try {
            return SearchIntent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SearchIntent.UNKNOWN;
        }
    }

    private static SearchFilters parseFilters(JsonNode node) {
        if (node == null || !node.isObject()) return SearchFilters.EMPTY;
        return new SearchFilters(
                textField(node, "brand"),
                textField(node, "category"),
                null
        );
    }
}
