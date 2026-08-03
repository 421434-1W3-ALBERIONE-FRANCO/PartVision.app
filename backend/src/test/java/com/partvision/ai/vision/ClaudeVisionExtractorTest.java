package com.partvision.ai.vision;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeVisionExtractorTest {

    // ---------- parsear (logica pura) ----------

    @Test
    void parsear_jsonCompleto() {
        String texto = """
                Aquí está el resultado:
                {"codigo_pieza":"ABC-123","marca":"Bosch","descripcion":"Filtro de aceite",
                 "codigo_barras":"7791234567890","detalles_extra":{"origen":"Argentina"}}""";

        ExtraccionIA r = ClaudeVisionExtractor.parsear(texto, "claude-opus-5");

        assertThat(r.codigoPieza()).isEqualTo("ABC-123");
        assertThat(r.marca()).isEqualTo("Bosch");
        assertThat(r.descripcion()).isEqualTo("Filtro de aceite");
        assertThat(r.codigoBarras()).isEqualTo("7791234567890");
        assertThat(r.detallesExtra()).containsEntry("origen", "Argentina");
        assertThat(r.modelo()).isEqualTo("claude-opus-5");
    }

    @Test
    void parsear_camposEnNull() {
        String texto = "{\"codigo_pieza\":null,\"marca\":null,\"descripcion\":null,"
                + "\"codigo_barras\":null,\"detalles_extra\":{}}";

        ExtraccionIA r = ClaudeVisionExtractor.parsear(texto, "m");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.marca()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_sinJson_devuelveTodoNull() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear("no hay json aca", "m");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
        assertThat(r.modelo()).isEqualTo("m");
    }

    @Test
    void parsear_textoNull_devuelveTodoNull() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear(null, "m");
        assertThat(r.descripcion()).isNull();
    }

    @Test
    void parsear_jsonInvalido_devuelveTodoNull() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear("{ roto sin comillas }", "m");
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_detallesExtraNoObjeto_seIgnora() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear(
                "{\"descripcion\":\"x\",\"detalles_extra\":\"no-es-objeto\"}", "m");
        assertThat(r.descripcion()).isEqualTo("x");
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_campoNoEscalar_seTomaComoNull() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear(
                "{\"marca\":{\"anidado\":1},\"descripcion\":\"x\",\"detalles_extra\":{}}", "m");
        assertThat(r.marca()).isNull();
        assertThat(r.descripcion()).isEqualTo("x");
    }

    @Test
    void parsear_llaveCierreAntesDeApertura_devuelveTodoNull() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear("} {", "m");
        assertThat(r.descripcion()).isNull();
    }

    @Test
    void parsear_detallesConValoresNoEscalares_soloTomaEscalares() {
        ExtraccionIA r = ClaudeVisionExtractor.parsear(
                "{\"detalles_extra\":{\"origen\":\"AR\",\"nulo\":null,\"obj\":{\"x\":1}}}", "m");
        assertThat(r.detallesExtra()).containsOnlyKeys("origen");
    }

    // ---------- extraer (con cliente mockeado) ----------

    @Test
    void extraer_llamaAlModeloYParsea() {
        AnthropicClient client = mock(AnthropicClient.class);
        MessageService messages = mock(MessageService.class);
        Message message = mock(Message.class);
        ContentBlock block = mock(ContentBlock.class);
        TextBlock textBlock = mock(TextBlock.class);

        when(client.messages()).thenReturn(messages);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of(block));
        when(block.text()).thenReturn(Optional.of(textBlock));
        when(textBlock.text()).thenReturn("{\"descripcion\":\"Filtro detectado\",\"detalles_extra\":{}}");

        ClaudeVisionExtractor extractor = new ClaudeVisionExtractor(client, "claude-opus-5");
        ExtraccionIA r = extractor.extraer(new byte[]{1, 2, 3}, "image/png");

        assertThat(r.descripcion()).isEqualTo("Filtro detectado");
        assertThat(r.modelo()).isEqualTo("claude-opus-5");
    }

    @Test
    void extraer_soportaContentTypeNuloYVariados() {
        AnthropicClient client = mock(AnthropicClient.class);
        MessageService messages = mock(MessageService.class);
        Message message = mock(Message.class);

        when(client.messages()).thenReturn(messages);
        when(messages.create(any(MessageCreateParams.class))).thenReturn(message);
        when(message.content()).thenReturn(List.of());

        ClaudeVisionExtractor extractor = new ClaudeVisionExtractor(client, "m");
        // distintos content types ejercitan el mapeo de media type
        assertThat(extractor.extraer(new byte[]{1}, null).descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/webp").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/gif").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/jpeg").descripcion()).isNull();
    }
}
