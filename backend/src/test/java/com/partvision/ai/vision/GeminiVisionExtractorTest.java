package com.partvision.ai.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class GeminiVisionExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------- parsear (logica pura, sin red) ----------

    @Test
    void parsear_jsonCompleto() {
        String texto = """
                Resultado:
                {"codigo_pieza":"XYZ-999","marca":"NGK","descripcion":"Bujia de encendido",
                 "codigo_barras":"7790000111222","detalles_extra":{"voltaje":"12V"}}""";

        ExtraccionIA r = GeminiVisionExtractor.parsear(texto, "gemini-2.0-flash");

        assertThat(r.codigoPieza()).isEqualTo("XYZ-999");
        assertThat(r.marca()).isEqualTo("NGK");
        assertThat(r.descripcion()).isEqualTo("Bujia de encendido");
        assertThat(r.codigoBarras()).isEqualTo("7790000111222");
        assertThat(r.detallesExtra()).containsEntry("voltaje", "12V");
        assertThat(r.modelo()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void parsear_camposEnNull() {
        String texto = "{\"codigo_pieza\":null,\"marca\":null,\"descripcion\":null,"
                + "\"codigo_barras\":null,\"detalles_extra\":{}}";

        ExtraccionIA r = GeminiVisionExtractor.parsear(texto, "m");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.marca()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_sinJson_devuelveTodoNull() {
        ExtraccionIA r = GeminiVisionExtractor.parsear("no hay json aca", "m");
        assertThat(r.codigoPieza()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
        assertThat(r.modelo()).isEqualTo("m");
    }

    @Test
    void parsear_textoNull_devuelveTodoNull() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(null, "m");
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_jsonInvalido_devuelveTodoNull() {
        ExtraccionIA r = GeminiVisionExtractor.parsear("{ roto sin comillas }", "m");
        assertThat(r.descripcion()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_detallesExtraNoObjeto_seIgnora() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(
                "{\"descripcion\":\"x\",\"detalles_extra\":\"no-es-objeto\"}", "m");
        assertThat(r.descripcion()).isEqualTo("x");
        assertThat(r.detallesExtra()).isEmpty();
    }

    @Test
    void parsear_campoNoEscalar_seTomaComoNull() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(
                "{\"marca\":{\"anidado\":1},\"descripcion\":\"x\",\"detalles_extra\":{}}", "m");
        assertThat(r.marca()).isNull();
        assertThat(r.descripcion()).isEqualTo("x");
    }

    @Test
    void parsear_llaveCierreAntesDeApertura_devuelveTodoNull() {
        ExtraccionIA r = GeminiVisionExtractor.parsear("} {", "m");
        assertThat(r.descripcion()).isNull();
    }

    @Test
    void parsear_detallesConValoresNoEscalares_soloTomaEscalares() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(
                "{\"detalles_extra\":{\"origen\":\"AR\",\"nulo\":null,\"obj\":{\"x\":1}}}", "m");
        assertThat(r.detallesExtra()).containsOnlyKeys("origen");
    }

    // ---------- extraerTexto (parseo de la respuesta de Gemini) ----------

    @Test
    void extraerTexto_concatenaPartsDeTexto() throws Exception {
        JsonNode resp = MAPPER.readTree("""
                {"candidates":[{"content":{"parts":[{"text":"parte1 "},{"text":"parte2"}]}}]}""");
        assertThat(GeminiVisionExtractor.extraerTexto(resp)).isEqualTo("parte1 parte2");
    }

    @Test
    void extraerTexto_respuestaNull_devuelveNull() {
        assertThat(GeminiVisionExtractor.extraerTexto(null)).isNull();
    }

    @Test
    void extraerTexto_sinCandidates_devuelveNull() throws Exception {
        JsonNode resp = MAPPER.readTree("{\"candidates\":[]}");
        assertThat(GeminiVisionExtractor.extraerTexto(resp)).isNull();
    }

    @Test
    void extraerTexto_partSinTexto_devuelveNull() throws Exception {
        JsonNode resp = MAPPER.readTree("""
                {"candidates":[{"content":{"parts":[{"inline_data":{}}]}}]}""");
        assertThat(GeminiVisionExtractor.extraerTexto(resp)).isNull();
    }

    // ---------- construirCuerpo ----------

    @Test
    void construirCuerpo_incluyePromptEImagenInline() {
        var cuerpo = GeminiVisionExtractor.construirCuerpo("QUJD", "image/png");
        assertThat(cuerpo).containsKeys("contents", "generationConfig");
        assertThat(MAPPER.valueToTree(cuerpo).toString())
                .contains("inline_data").contains("image/png").contains("QUJD");
    }

    // ---------- extraer (con servidor REST mockeado) ----------

    @Test
    void extraer_llamaAlEndpointYParsea() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-2.0-flash:generateContent"))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[
                          {"text":"{\\"descripcion\\":\\"Bujia detectada\\",\\"detalles_extra\\":{}}"}]}}]}""",
                        MediaType.APPLICATION_JSON));

        GeminiVisionExtractor extractor =
                new GeminiVisionExtractor(builder.build(), "gemini-2.0-flash", "test-key");
        ExtraccionIA r = extractor.extraer(new byte[]{1, 2, 3}, "image/png");

        assertThat(r.descripcion()).isEqualTo("Bujia detectada");
        assertThat(r.modelo()).isEqualTo("gemini-2.0-flash");
        server.verify();
    }

    @Test
    void extraer_soportaContentTypesVariados() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test/v1beta");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.ExpectedCount.times(5),
                        requestTo("http://gemini.test/v1beta/models/m:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        GeminiVisionExtractor extractor = new GeminiVisionExtractor(builder.build(), "m", "k");

        // Ejercita todas las ramas de resolverContentType()
        assertThat(extractor.extraer(new byte[]{1}, null).descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/png").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/gif").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/webp").descripcion()).isNull();
        assertThat(extractor.extraer(new byte[]{1}, "image/bmp").descripcion()).isNull();
        server.verify();
    }

    @Test
    void extraer_sinApiKey_lanza() {
        RestClient client = RestClient.builder().baseUrl("http://gemini.test").build();
        GeminiVisionExtractor extractor = new GeminiVisionExtractor(client, "m", "  ");

        assertThatThrownBy(() -> extractor.extraer(new byte[]{1}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }
}
