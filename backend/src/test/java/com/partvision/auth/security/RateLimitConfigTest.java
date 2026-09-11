package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginRateLimitFilter_registraEnLoginUrl() {
        FilterRegistrationBean<IpRateLimitFilter> bean =
                config.loginRateLimitFilter(5, 1, objectMapper);

        assertThat(bean.getUrlPatterns()).containsExactly("/api/v1/auth/login");
        assertThat(bean.getFilter()).isInstanceOf(IpRateLimitFilter.class);
        assertThat(bean.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void recoveryRateLimitFilter_registraEnRecoveryUrls() {
        FilterRegistrationBean<IpRateLimitFilter> bean =
                config.recoveryRateLimitFilter(objectMapper);

        assertThat(bean.getUrlPatterns()).containsExactlyInAnyOrder(
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/2fa/recover-request",
                "/api/v1/auth/2fa/recover-confirm");
        assertThat(bean.getFilter()).isInstanceOf(IpRateLimitFilter.class);
        assertThat(bean.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
    }

    @Test
    void comprasRateLimitFilter_registraEnLaRecepcion() {
        FilterRegistrationBean<IpRateLimitFilter> bean =
                config.comprasRateLimitFilter(30, 1, objectMapper);

        assertThat(bean.getUrlPatterns()).containsExactly("/api/v1/compras/recepcion");
        assertThat(bean.getFilter()).isInstanceOf(IpRateLimitFilter.class);
        assertThat(bean.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 2);
    }

    /** El tope de tamano tiene que correr antes que nada: es lo que evita el gasto de memoria. */
    @Test
    void comprasRequestSizeFilter_registraEnLaRecepcionYCorrePrimero() {
        FilterRegistrationBean<RequestSizeLimitFilter> bean =
                config.comprasRequestSizeFilter(2_097_152, objectMapper);

        assertThat(bean.getUrlPatterns()).containsExactly("/api/v1/compras/recepcion");
        assertThat(bean.getFilter()).isInstanceOf(RequestSizeLimitFilter.class);
        assertThat(bean.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
