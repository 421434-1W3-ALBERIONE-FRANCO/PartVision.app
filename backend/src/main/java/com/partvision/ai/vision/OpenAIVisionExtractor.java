package com.partvision.ai.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor de vision real contra la API de OpenAI (SDK oficial, gpt-4o).
 * Se activa con la propiedad {@code ai.vision.provider=openai} y requiere
 * la variable de entorno {@code OPENAI_API_KEY}.
 * <p>
 * El backend SIEMPRE valida el resultado; nunca confia ciegamente en el JSON
 * devuelto. Si el modelo no detecta un campo, devuelve null (nunca inventa).
 */
@Component
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "openai")
public class OpenAIVisionExtractor implements VisionExtractor {

    static final String PROMPT = """
            Sos un asistente que extrae datos de una foto de un repuesto automotor \
            (caja, etiqueta o el producto). Devolve UNICAMENTE un objeto JSON con exactamente \
            estas claves: codigo_pieza, marca, descripcion, codigo_barras, detalles_extra. \
            Reglas estrictas: si un dato no se ve con claridad en la imagen, pone null. \
            No inventes datos. No deduzcas compatibilidad con vehiculos. \
            detalles_extra es un objeto con atributos sueltos que veas (voltaje, medidas, \
            origen, material, etc.) o {} si no hay ninguno. No agregues texto fuera del JSON.""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIClient client;
    private final String model;

    public OpenAIVisionExtractor(OpenAIClient client,
                                 @Value("${ai.vision.model:gpt-4o}") String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public ExtraccionIA extraer(byte[] imagen, String contentType) {
        String texto = obtenerTextoRespuesta(imagen, contentType);
        return parsear(texto, model);
    }

    /** Llamada al modelo de vision. Aislada para poder testear el parseo por separado. */
    protected String obtenerTextoRespuesta(byte[] imagen, String contentType) {
        String base64 = Base64.getEncoder().encodeToString(imagen);
        String dataUri = "data:" + resolverContentType(contentType) + ";base64," + base64;

        ChatCompletionContentPartImage imagePart = ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                        .url(dataUri)
                        .build())
                .build();

        ChatCompletionContentPartText textPart = ChatCompletionContentPartText.builder()
                .text(PROMPT)
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(1024L)
                .addUserMessageOfArrayOfContentParts(List.of(
                        ChatCompletionContentPart.ofImageUrl(imagePart),
                        ChatCompletionContentPart.ofText(textPart)))
                .build();

        ChatCompletion completion = client.chat().completions().create(params);
        return completion.choices().stream()
                .findFirst()
                .flatMap(c -> c.message().content())
                .orElse(null);
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
