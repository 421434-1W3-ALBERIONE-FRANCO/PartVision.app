package com.partvision.ai.vision;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor de vision real contra la API de Anthropic (SDK oficial).
 * Se activa con la propiedad {@code ai.vision.provider=claude} y requiere
 * la variable de entorno {@code ANTHROPIC_API_KEY}. El backend SIEMPRE valida
 * el resultado; nunca confia ciegamente en el JSON devuelto.
 */
@Component
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "claude")
public class ClaudeVisionExtractor implements VisionExtractor {

    private static final String PROMPT = """
            Sos un asistente que extrae datos de una foto de un repuesto automotor \
            (caja, etiqueta o el producto). Devolve UNICAMENTE un objeto JSON con exactamente \
            estas claves: codigo_pieza, marca, descripcion, codigo_barras, detalles_extra. \
            Reglas estrictas: si un dato no se ve con claridad en la imagen, poné null. \
            No inventes datos. No deduzcas compatibilidad con vehiculos. \
            detalles_extra es un objeto con atributos sueltos que veas (voltaje, medidas, \
            origen, material, etc.) o {} si no hay ninguno. No agregues texto fuera del JSON.""";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnthropicClient client;
    private final String model;

    public ClaudeVisionExtractor(AnthropicClient client,
                                 @Value("${ai.vision.model:claude-opus-5}") String model) {
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
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .mediaType(mediaType(contentType))
                                        .data(base64)
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())))
                .build();

        Message message = client.messages().create(params);
        StringBuilder sb = new StringBuilder();
        for (var block : message.content()) {
            block.text().map(TextBlock::text).ifPresent(sb::append);
        }
        return sb.toString();
    }

    private Base64ImageSource.MediaType mediaType(String contentType) {
        if (contentType == null) {
            return Base64ImageSource.MediaType.IMAGE_JPEG;
        }
        return switch (contentType) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> Base64ImageSource.MediaType.IMAGE_JPEG;
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
