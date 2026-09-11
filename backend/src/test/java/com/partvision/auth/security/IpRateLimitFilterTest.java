package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IpRateLimitFilterTest {

    private final IpRateLimitFilter filter =
            new IpRateLimitFilter(5, Duration.ofMinutes(1), new ObjectMapper());

    private MockHttpServletResponse postLogin(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void permiteHastaElLimiteYLuegoBloqueaCon429() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertThat(postLogin("1.2.3.4").getStatus())
                    .as("intento %d dentro del limite", i)
                    .isEqualTo(200);
        }
        MockHttpServletResponse bloqueado = postLogin("1.2.3.4");
        assertThat(bloqueado.getStatus()).isEqualTo(429);
        assertThat(bloqueado.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueado.getContentAsString()).contains("Demasiados intentos");
    }

    @Test
    void cadaIpTieneSuPropioLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            postLogin("10.0.0.1");
        }
        // La IP saturada se bloquea, pero otra IP sigue habilitada.
        assertThat(postLogin("10.0.0.1").getStatus()).isEqualTo(429);
        assertThat(postLogin("10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void noLimitaMetodosDistintosDePost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
    @Test
    void mensajePersonalizado_seUsaEnEl429() throws Exception {
        IpRateLimitFilter propio = new IpRateLimitFilter(
                1, Duration.ofMinutes(1), new ObjectMapper(), "Frena un poco");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/compras/recepcion");
        request.addHeader("X-Forwarded-For", "7.7.7.7");
        propio.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse bloqueado = new MockHttpServletResponse();
        propio.doFilter(request, bloqueado, new MockFilterChain());

        assertThat(bloqueado.getStatus()).isEqualTo(429);
        assertThat(bloqueado.getContentAsString()).contains("Frena un poco");
    }

    @Test
    void sinForwardedFor_usaLaIpRemota() throws Exception {
        IpRateLimitFilter propio = new IpRateLimitFilter(1, Duration.ofMinutes(1), new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.9");
        propio.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse bloqueado = new MockHttpServletResponse();
        propio.doFilter(request, bloqueado, new MockFilterChain());
        assertThat(bloqueado.getStatus()).isEqualTo(429);
    }
}
