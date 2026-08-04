package com.partvision.ai.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor de vision real contra la API REST de Google Gemini (generateContent).
 * Se activa con la propiedad {@code ai.vision.provider=gemini} y requiere la
 * variable de entorno {@code GEMINI_API_KEY} (nivel gratuito de Google AI Studio).
 * <p>
 * El backend SIEMPRE valida el resultado; nunca confia ciegamente en el JSON
 * devuelto. Si el modelo no detecta un campo, devuelve null (nunca inventa).
 */
@Component
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "gemini")
public class GeminiVisionExtractor implements VisionExtractor {

    static final String PROMPT = """
            Sos un asistente que extrae datos de una foto de un repuesto automotor \
            (caja, etiqueta o el producto). Devolve UNICAMENTE un objeto JSON con exactamente \
            estas claves: codigo_pieza, marca, descripcion, codigo_barras, detalles_extra. \
            Reglas estrictas: si un dato no se ve con claridad en la imagen, poné null. \
            No inventes datos. No deduzcas compatibilidad con vehiculos. \
            detalles_extra es un objeto con atributos sueltos que veas (voltaje, medidas, \
            origen, material, etc.) o {} si no hay ninguno. Si en la imagen hay varias cajas o \
            etiquetas, extrae SOLO la del producto central o mas prominente. \
            No agregues texto fuera del JSON.""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String model;
    private final String apiKey;

    public GeminiVisionExtractor(RestClient geminiRestClient,
                                 @Value("${ai.vision.model:gemini-flash-latest}") String model,
                                 @Value("${GEMINI_API_KEY:}") String apiKey) {
        this.client = geminiRestClient;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public ExtraccionIA extraer(byte[] imagen, String contentType) {
        String texto = obtenerTextoRespuesta(imagen, contentType);
        return parsear(texto, model);
    }

    /** Llamada al modelo de vision. Aislada para poder testear el parseo por separado. */
    protected String obtenerTextoRespuesta(byte[] imagen, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY no configurada para el proveedor de vision 'gemini'.");
        }
        String base64 = Base64.getEncoder().encodeToString(imagen);
        Map<String, Object> cuerpo = construirCuerpo(base64, resolverContentType(contentType));

        JsonNode respuesta = client.post()
                .uri("/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey) // en header (no en la URL) para no filtrarla en logs
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);

        return extraerTexto(respuesta);
    }

    /** Arma el cuerpo de la request generateContent: prompt de texto + imagen inline en base64. */
    static Map<String, Object> construirCuerpo(String base64, String mimeType) {
        Map<String, Object> textPart = Map.of("text", PROMPT);
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", mimeType, "data", base64));
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        // maxOutputTokens alto a proposito: los modelos Gemini 2.x gastan tokens de
        // "thinking" (thoughtsTokenCount) ANTES de emitir la respuesta; con un limite
        // bajo (ej: 1024) el JSON se corta a la mitad (finishReason=MAX_TOKENS) y no parsea.
        return Map.of(
                "contents", List.of(content),
                "generationConfig", Map.of("maxOutputTokens", 8192));
    }

    /** Extrae el texto concatenado de candidates[0].content.parts[].text. */
    static String extraerTexto(JsonNode respuesta) {
        if (respuesta == null) {
            return null;
        }
        JsonNode parts = respuesta.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            JsonNode texto = part.get("text");
            if (texto != null && texto.isValueNode()) {
                sb.append(texto.asText());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String resolverContentType(String contentType) {
        if (contentType == null) {
            return "image/jpeg";
        }
        return switch (contentType) {
            case "image/png", "image/gif", "image/webp" -> contentType;
            default -> "image/jpeg";
        };
    }

    /** Parsea la respuesta del modelo de forma defensiva; nunca lanza. */
    static ExtraccionIA parsear(String texto, String modelo) {
        String json = extraerJson(texto);
        if (json == null) {
            return new ExtraccionIA(null, null, null, null, Map.of(), modelo);
        }
        try {
            JsonNode nodo = MAPPER.readTree(json);
            return new ExtraccionIA(
                    texto(nodo, "codigo_pieza"),
                    texto(nodo, "marca"),
                    texto(nodo, "descripcion"),
                    texto(nodo, "codigo_barras"),
                    detalles(nodo.get("detalles_extra")),
                    modelo);
        } catch (Exception ex) {
            return new ExtraccionIA(null, null, null, null, Map.of(), modelo);
        }
    }

    private static String extraerJson(String texto) {
        if (texto == null) {
            return null;
        }
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        return (inicio >= 0 && fin > inicio) ? texto.substring(inicio, fin + 1) : null;
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return (valor == null || valor.isNull() || !valor.isValueNode()) ? null : valor.asText();
    }

    private static Map<String, Object> detalles(JsonNode nodo) {
        if (nodo == null || !nodo.isObject()) {
            return Map.of();
        }
        Map<String, Object> mapa = new HashMap<>();
        nodo.fields().forEachRemaining(e -> {
            if (e.getValue().isValueNode() && !e.getValue().isNull()) {
                mapa.put(e.getKey(), e.getValue().asText());
            }
        });
        return mapa;
    }
}
