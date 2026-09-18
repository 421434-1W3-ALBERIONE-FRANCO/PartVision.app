package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De donde sale la IP con la que se cuenta el limite. nginx arma X-Forwarded-For con
 * {@code $proxy_add_x_forwarded_for}: lo que mando el cliente, y al final la IP real.
 * El 2026-09-17 se comprobo en produccion que 40 requests con un header falso distinto
 * pasaban todos: el filtro leia el primer valor, que escribe el cliente.
 */
class IpRateLimitFilterIpRealTest {

    private static MockHttpServletRequest desde(String remota, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/compras/recepcion");
        request.setRemoteAddr(remota);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        return request;
    }

    /** Lo que nginx produce cuando el cliente falsifica el header. */
    @Test
    void headerFalsificado_cuentaLaIpQueAgregoNginx() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", "10.66.0.7, 203.0.113.50")))
                .isEqualTo("203.0.113.50");
    }

    @Test
    void sinHeaderFalso_nginxDejaSoloLaIpReal() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", "203.0.113.50")))
                .isEqualTo("203.0.113.50");
    }

    /** El ataque concreto: cambiar el primer valor en cada request ya no reparte el limite. */
    @Test
    void rotarElPrimerValor_noEsquivaElLimite() throws Exception {
        IpRateLimitFilter filter = new IpRateLimitFilter(3, Duration.ofMinutes(1), new ObjectMapper());

        int bloqueados = 0;
        for (int i = 1; i <= 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(desde("127.0.0.1", "10.66.0." + i + ", 203.0.113.50"), response,
                    new MockFilterChain());
            if (response.getStatus() == 429) {
                bloqueados++;
            }
        }

        assertThat(bloqueados).isEqualTo(7);
    }

    /**
     * Si la conexion no viene del propio host, el header no lo puso nuestro nginx: se ignora.
     * Cubre el caso de que el backend vuelva a quedar publicado por error.
     */
    @Test
    void conexionDirectaDesdeAfuera_ignoraElHeader() {
        assertThat(IpRateLimitFilter.clientIp(desde("198.51.100.9", "10.66.0.7")))
                .isEqualTo("198.51.100.9");
    }

    @Test
    void loopbackIpv6_leElHeader() {
        assertThat(IpRateLimitFilter.clientIp(desde("0:0:0:0:0:0:0:1", "203.0.113.50")))
                .isEqualTo("203.0.113.50");
        assertThat(IpRateLimitFilter.clientIp(desde("::1", "203.0.113.50")))
                .isEqualTo("203.0.113.50");
    }

    @Test
    void loopbackMapeadoAIpv6_leElHeader() {
        assertThat(IpRateLimitFilter.clientIp(desde("::ffff:127.0.0.1", "203.0.113.50")))
                .isEqualTo("203.0.113.50");
    }

    @Test
    void sinHeader_usaLaIpDeLaConexion() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", null))).isEqualTo("127.0.0.1");
    }

    @Test
    void headerEnBlanco_usaLaIpDeLaConexion() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", "   "))).isEqualTo("127.0.0.1");
    }

    /** Headers rotos no pueden tirar una excepcion: ",".split(",") devuelve un array vacio. */
    @Test
    void headerSoloComas_usaLaIpDeLaConexion() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", ","))).isEqualTo("127.0.0.1");
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", ",,,"))).isEqualTo("127.0.0.1");
    }

    @Test
    void ultimoValorVacio_usaLaIpDeLaConexion() {
        assertThat(IpRateLimitFilter.clientIp(desde("127.0.0.1", "203.0.113.50, "))).isEqualTo("127.0.0.1");
    }

    /** El mapa de baldes no admite claves null: sin direccion no puede terminar en un 500. */
    @Test
    void sinIpRemota_usaUnBaldeComun() {
        MockHttpServletRequest request = desde("127.0.0.1", "203.0.113.50");
        request.setRemoteAddr(null);

        assertThat(IpRateLimitFilter.clientIp(request)).isEqualTo("desconocida");
    }
}
