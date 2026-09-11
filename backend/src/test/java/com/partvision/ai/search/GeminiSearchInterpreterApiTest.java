package com.partvision.ai.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * La llamada a la API y el saneado de lo que vuelve. La interpretacion de la consulta es
 * texto del usuario concatenado a un prompt: lo que el modelo devuelva se valida contra un
 * esquema y nunca se ejecuta, y estas pruebas cubren justamente ese borde.
 */
class GeminiSearchInterpreterApiTest {

    private static final String URL = "http://ai.test/models/gemini-flash-latest:generateContent";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeminiSearchInterpreter interpreter(RestClient client, String apiKey) {
        return new GeminiSearchInterpreter(client, apiKey,
                new AiSearchProperties(true, "gemini", "gemini-flash-latest", 1000, 0.70, 720));
    }

    private GeminiSearchInterpreter sinApi() {
        return interpreter(null, "");
    }

    @Test
    void interpret_sinApiKey_niIntentaLlamar() {
        assertThat(sinApi().interpret("pastillas")).isEmpty();
    }

    @Test
    void interpret_apiKeyNula_niIntentaLlamar() {
        assertThat(interpreter(null, null).interpret("pastillas")).isEmpty();
    }

    @Test
    void interpret_respuestaValida_devuelveLaInterpretacion() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        String textoDelModelo = "{\"normalizedQuery\":\"pistones\",\"terms\":[\"pistones\"],\"confidence\":0.9}";
        String cuerpo = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + MAPPER.valueToTree(textoDelModelo) + "}]}}]}";

        server.expect(requestTo(URL))
                .andExpect(header("x-goog-api-key", "clave"))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));

        Optional<SearchInterpretation> result = interpreter(builder.build(), "clave").interpret("pistones");

        assertThat(result).isPresent();
        assertThat(result.get().terms()).containsExactly("pistones");
        assertThat(result.get().originalQuery()).isEqualTo("pistones");
        server.verify();
    }

    /** Si la API falla o tarda, la busqueda sigue sin IA en lugar de romperse. */
    @Test
    void interpret_errorDeLaApi_devuelveVacio() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(interpreter(builder.build(), "clave").interpret("pistones")).isEmpty();
    }

    @Test
    void buildRequestBody_mandaElPromptDeSistemaMasLaConsulta() {
        Map<String, Object> body = sinApi().buildRequestBody("pastillas gol");

        assertThat(body.toString()).contains("Consulta del usuario: pastillas gol", "No inventes marcas");
        assertThat(body.get("generationConfig").toString()).contains("application/json");
    }

    @Test
    void extractText_respuestaNula_devuelveNull() {
        assertThat(GeminiSearchInterpreter.extractText(null)).isNull();
    }

    @Test
    void extractText_sinPartes_devuelveNull() throws Exception {
        assertThat(GeminiSearchInterpreter.extractText(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":[]}}]}"))).isNull();
    }

    @Test
    void extractText_partesQueNoSonArray_devuelveNull() throws Exception {
        assertThat(GeminiSearchInterpreter.extractText(
                MAPPER.readTree("{\"candidates\":[{\"content\":{\"parts\":\"x\"}}]}"))).isNull();
    }

    @Test
    void extractText_variasPartes_concatenaYSalteaLasQueNoSonTexto() throws Exception {
        var node = MAPPER.readTree(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ab\"},{\"otro\":1},{\"text\":\"cd\"}]}}]}");

        assertThat(GeminiSearchInterpreter.extractText(node)).isEqualTo("abcd");
    }

    @Test
    void extractText_textoQueNoEsValor_seSaltea() throws Exception {
        var node = MAPPER.readTree(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":{\"a\":1}}]}}]}");

        assertThat(GeminiSearchInterpreter.extractText(node)).isNull();
    }

    @Test
    void parseAndValidate_terminosVaciosLargosONoTextuales_seDescartan() {
        String json = "{\"normalizedQuery\":\"test\","
                + "\"terms\":[\"  \",{\"objeto\":1},\"valido\",\"" + "x".repeat(60) + "\"],"
                + "\"confidence\":0.8}";

        Optional<SearchInterpretation> result = sinApi().parseAndValidate("test", json);

        assertThat(result).isPresent();
        assertThat(result.get().terms()).containsExactly("valido");
    }

    @Test
    void parseAndValidate_normalizedQueryNoTextual_devuelveVacio() {
        assertThat(sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":{\"a\":1},\"terms\":[\"x\"],\"confidence\":0.8}")).isEmpty();
    }

    @Test
    void parseAndValidate_normalizedQueryNula_devuelveVacio() {
        assertThat(sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":null,\"terms\":[\"x\"],\"confidence\":0.8}")).isEmpty();
    }

    @Test
    void parseAndValidate_sinTerms_devuelveVacio() {
        assertThat(sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":\"test\",\"confidence\":0.8}")).isEmpty();
    }

    @Test
    void parseAndValidate_termsQueNoEsArray_devuelveVacio() {
        assertThat(sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":\"test\",\"terms\":\"pistones\",\"confidence\":0.8}")).isEmpty();
    }

    @Test
    void parseAndValidate_filtrosQueNoSonObjeto_quedanVacios() {
        Optional<SearchInterpretation> result = sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":\"test\",\"terms\":[\"x\"],\"filters\":\"nada\",\"confidence\":0.8}");

        assertThat(result).isPresent();
        assertThat(result.get().filters()).isEqualTo(SearchFilters.EMPTY);
    }

    @Test
    void parseAndValidate_sinConfidence_quedaEnCero() {
        Optional<SearchInterpretation> result = sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":\"test\",\"terms\":[\"x\"]}");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isZero();
    }

    @Test
    void parseAndValidate_confidenceNegativa_quedaEnCero() {
        Optional<SearchInterpretation> result = sinApi().parseAndValidate("test",
                "{\"normalizedQuery\":\"test\",\"terms\":[\"x\"],\"confidence\":-3}");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isZero();
    }

    @Test
    void parseAndValidate_jsonRoto_devuelveVacio() {
        assertThat(sinApi().parseAndValidate("test", "{no es json}")).isEmpty();
    }
}
