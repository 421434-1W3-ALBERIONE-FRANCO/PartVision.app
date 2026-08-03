package com.partvision.ai.vision;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAIVisionExtractorTest {

    // ---------- parsear (logica pura, sin mock) ----------

    @Test
    void parsear_jsonCompleto() {
        String texto = """
                Aquí está el resultado:
                {"codigo_pieza":"XYZ-999","marca":"NGK","descripcion":"Bujia de encendido",
                 "codigo_barras":"7790000111222","detalles_extra":{"voltaje":"12V"}}""";

        ExtraccionIA r = OpenAIVisionExtractor.parsear(texto, "gpt-4o");

        assertThat(r.codigoPieza()).isEqualTo("XYZ-999");
        assertThat(r.marca()).isEqualTo("NGK");
        assertThat(r.descripcion()).isEqualTo("Bujia de encendido");
        assertThat(r.codigoBarras()).isEqualTo("7790000111222");
        assertThat(r.detallesExtra()).containsEntry("voltaje", "12V");
        assertThat(r.modelo()).isEqualTo("gpt-4o");
    }

    @Test
    void parsear_camposEnNull() {
        String texto = "{\"codigo_pieza\":null,\"marca\":null,\"descripcion\":null,"
                + "\"codigo_barras\":null,\"detalles_extra\":{}}";

        ExtraccionIA r = OpenAIVisionExtractor.parsear(texto, "m");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.marca()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_sinJson_devuelveTodoNull() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear("no hay json aca", "m");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
        assertThat(r.modelo()).isEqualTo("m");
    }

    @Test
    void parsear_textoNull_devuelveTodoNull() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear(null, "m");
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_jsonInvalido_devuelveTodoNull() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear("{ roto sin comillas }", "m");
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_detallesExtraNoObjeto_seIgnora() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear(
                "{\"descripcion\":\"x\",\"detalles_extra\":\"no-es-objeto\"}", "m");
        assertThat(r.descripcion()).isEqualTo("x");
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_campoNoEscalar_seTomaComoNull() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear(
                "{\"marca\":{\"anidado\":1},\"descripcion\":\"x\",\"detalles_extra\":{}}", "m");
        assertThat(r.marca()).isNull();
        assertThat(r.descripcion()).isEqualTo("x");
    }

    @Test
    void parsear_llaveCierreAntesDeApertura_devuelveTodoNull() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear("} {", "m");
        assertThat(r.descripcion()).isNull();
    }

    @Test
    void parsear_detallesConValoresNoEscalares_soloTomaEscalares() {
        ExtraccionIA r = OpenAIVisionExtractor.parsear(
                "{\"detalles_extra\":{\"origen\":\"AR\",\"nulo\":null,\"obj\":{\"x\":1}}}", "m");
        assertThat(r.detallesExtra()).containsOnlyKeys("origen");
    }

    // ---------- extraer (con cliente mockeado) ----------

    @Test
    void extraer_llamaAlModeloYParsea() {
        OpenAIClient client = mock(OpenAIClient.class);
        ChatService chatService = mock(ChatService.class);
        ChatCompletionService completionService = mock(ChatCompletionService.class);
        ChatCompletion completion = mock(ChatCompletion.class);
        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
        when(completion.choices()).thenReturn(List.of(choice));
        when(choice.message()).thenReturn(message);
        when(message.content()).thenReturn(
                Optional.of("{\"descripcion\":\"Bujia detectada\",\"detalles_extra\":{}}"));

        OpenAIVisionExtractor extractor = new OpenAIVisionExtractor(client, "gpt-4o");
        ExtraccionIA r = extractor.extraer(new byte[]{1, 2, 3}, "image/png");

        assertThat(r.descripcion()).isEqualTo("Bujia detectada");
        assertThat(r.modelo()).isEqualTo("gpt-4o");
    }

    @Test
    void extraer_sinChoices_devuelveNull() {
        OpenAIClient client = mock(OpenAIClient.class);
        ChatService chatService = mock(ChatService.class);
        ChatCompletionService completionService = mock(ChatCompletionService.class);
        ChatCompletion completion = mock(ChatCompletion.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
        when(completion.choices()).thenReturn(List.of());

        OpenAIVisionExtractor extractor = new OpenAIVisionExtractor(client, "gpt-4o");
        ExtraccionIA r = extractor.extraer(new byte[]{1}, "image/jpeg");

        assertThat(r.descripcion()).isNull();
    }

    @Test
    void extraer_soportaContentTypesVariados() {
        OpenAIClient client = mock(OpenAIClient.class);
        ChatService chatService = mock(ChatService.class);
        ChatCompletionService completionService = mock(ChatCompletionService.class);
        ChatCompletion completion = mock(ChatCompletion.class);

        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
        when(completion.choices()).thenReturn(List.of());

        OpenAIVisionExtractor extractor = new OpenAIVisionExtractor(client, "m");

        // Ejercita todas las ramas del resolverContentType()
        assertThat(extractor.extraer(new byte[]{1}, null).descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/png").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/gif").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/webp").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/bmp").descripcion()).isNull();
    }
}
