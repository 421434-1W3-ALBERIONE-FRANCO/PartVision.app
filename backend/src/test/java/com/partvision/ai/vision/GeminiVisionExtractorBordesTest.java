package com.partvision.ai.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Respuestas incompletas del modelo de vision y la falta de credenciales. */
class GeminiVisionExtractorBordesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Sin API key hay que fallar claro, no mandar una llamada que va a rebotar. */
    @Test
    void sinApiKey_avisaQueFaltaConfigurar() {
        GeminiVisionExtractor extractor =
                new GeminiVisionExtractor(RestClient.builder().build(), "gemini-2.0-flash", "");

        assertThatThrownBy(() -> extractor.extraer(new byte[]{1, 2}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void apiKeyNula_avisaIgual() {
        GeminiVisionExtractor extractor =
                new GeminiVisionExtractor(RestClient.builder().build(), "gemini-2.0-flash", null);

        assertThatThrownBy(() -> extractor.extraer(new byte[]{1, 2}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void respuestaNula_devuelveNull() {
        assertThat(GeminiVisionExtractor.extraerTexto(null)).isNull();
    }

    @Test
    void respuestaSinPartes_devuelveNull() throws Exception {
        assertThat(GeminiVisionExtractor.extraerTexto(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":[]}}]}"))).isNull();
    }

    @Test
    void partesQueNoSonArray_devuelveNull() throws Exception {
        assertThat(GeminiVisionExtractor.extraerTexto(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":\"x\"}}]}"))).isNull();
    }

    @Test
    void partesSinTextoUtil_devuelveNull() throws Exception {
        assertThat(GeminiVisionExtractor.extraerTexto(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":[{\"otro\":1}]}}]}"))).isNull();
    }

    @Test
    void textoQueNoEsValor_seSaltea() throws Exception {
        assertThat(GeminiVisionExtractor.extraerTexto(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":{\"a\":1}},{\"text\":\"ok\"}]}}]}")))
                .isEqualTo("ok");
    }

    /** Si 'detalles' no viene como objeto se ignora, en vez de romper la extraccion. */
    @Test
    void detallesQueNoSonObjeto_quedanVacios() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(
                "{\"descripcion\":\"Filtro\",\"detalles_extra\":\"no soy un objeto\"}", "m");

        assertThat(r.detallesExtra()).isEmpty();
        assertThat(r.descripcion()).isEqualTo("Filtro");
    }

    @Test
    void detallesComoObjeto_seCopianLosValoresSimples() {
        ExtraccionIA r = GeminiVisionExtractor.parsear(
                "{\"descripcion\":\"Filtro\",\"detalles_extra\":{\"medida\":\"10mm\",\"anidado\":{\"x\":1},\"nulo\":null}}",
                "m");

        assertThat(r.detallesExtra()).containsExactly(java.util.Map.entry("medida", "10mm"));
    }
}
