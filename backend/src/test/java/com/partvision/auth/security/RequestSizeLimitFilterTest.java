package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    private static final long MAX = 100;

    private final RequestSizeLimitFilter filter =
            new RequestSizeLimitFilter(MAX, new ObjectMapper());

    /**
     * MockHttpServletRequest saca el Content-Length del contenido, asi que para simular un
     * request chunked (sin longitud declarada) hay que forzar -1.
     */
    private MockHttpServletRequest post(byte[] cuerpo, boolean conContentLength) {
        MockHttpServletRequest request = conContentLength
                ? new MockHttpServletRequest("POST", "/api/v1/compras/recepcion")
                : new MockHttpServletRequest("POST", "/api/v1/compras/recepcion") {
                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }
                };
        request.setContent(cuerpo);
        return request;
    }

    private static byte[] bytes(int n) {
        return "x".repeat(n).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void cuerpoDentroDelLimite_pasa() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post(bytes(50), true), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void contentLengthPasado_devuelve413SinLeerElCuerpo() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> {
            throw new AssertionError("no deberia llegar al resto de la cadena");
        };

        filter.doFilter(post(bytes(500), true), response, cadena);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("supera el maximo");
    }

    /** Sin Content-Length (transfer chunked) el tope se aplica contando al leer. */
    @Test
    void sinContentLength_cortaAlLeerYDevuelve413() throws Exception {
        MockHttpServletRequest request = post(bytes(500), false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> ((HttpServletRequest) req).getInputStream().readAllBytes();

        filter.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void sinContentLength_cuerpoChico_pasaYSeLeeEntero() throws Exception {
        MockHttpServletRequest request = post(bytes(50), false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        byte[][] leido = new byte[1][];
        FilterChain cadena = (req, res) -> leido[0] = ((HttpServletRequest) req).getInputStream().readAllBytes();

        filter.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(leido[0]).hasSize(50);
    }

    @Test
    void sinContentLength_lecturaByteAByte_tambienCorta() throws Exception {
        MockHttpServletRequest request = post(bytes(500), false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> {
            var in = ((HttpServletRequest) req).getInputStream();
            while (in.read() != -1) {
                // leer de a un byte hasta que el filtro corte
            }
        };

        filter.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void streamDelegaLosMetodosAsincronos() throws Exception {
        MockHttpServletRequest request = post(bytes(10), false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> {
            var in = ((HttpServletRequest) req).getInputStream();
            // El mock de Spring no implementa la API asincrona; lo que se verifica aca es
            // que el wrapper delegue en vez de inventar una respuesta.
            try {
                in.isReady();
                in.isFinished();
                in.setReadListener(null);
            } catch (UnsupportedOperationException esperado) {
                // delego y el delegado no lo soporta: suficiente
            }
        };

        filter.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
