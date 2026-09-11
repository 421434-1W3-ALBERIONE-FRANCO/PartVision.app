package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> loginRateLimitFilter(
            @Value("${security.rate-limit.login.capacity:5}") int capacity,
            @Value("${security.rate-limit.login.refill-minutes:1}") long refillMinutes,
            ObjectMapper objectMapper) {
        IpRateLimitFilter filter =
                new IpRateLimitFilter(capacity, Duration.ofMinutes(refillMinutes), objectMapper);

        FilterRegistrationBean<IpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/auth/login");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> recoveryRateLimitFilter(ObjectMapper objectMapper) {
        IpRateLimitFilter filter =
                new IpRateLimitFilter(5, Duration.ofMinutes(15), objectMapper);

        FilterRegistrationBean<IpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/2fa/recover-request",
                "/api/v1/auth/2fa/recover-confirm");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * La recepcion de compras es publica (Power Automate no se loguea) y escribe en la base:
     * sin freno, una IP puede probar API keys o inundar de facturas. El limite es holgado para
     * un flujo real —una factura cada pocos segundos— y corta el abuso.
     */
    @Bean
    public FilterRegistrationBean<IpRateLimitFilter> comprasRateLimitFilter(
            @Value("${security.rate-limit.compras.capacity:30}") int capacity,
            @Value("${security.rate-limit.compras.refill-minutes:1}") long refillMinutes,
            ObjectMapper objectMapper) {
        IpRateLimitFilter filter = new IpRateLimitFilter(
                capacity, Duration.ofMinutes(refillMinutes), objectMapper,
                "Demasiadas solicitudes de recepcion. Espera un momento e intenta de nuevo.");

        FilterRegistrationBean<IpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/compras/recepcion");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    /**
     * Tope de tamano del cuerpo en la recepcion. Va ANTES del rate limit porque Spring
     * deserializa el JSON entero antes de que el controller mire la API key: sin esto, un
     * anonimo puede hacer que el backend aloque el maximo que deje nginx, por request.
     */
    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> comprasRequestSizeFilter(
            @Value("${security.request-size.compras-bytes:2097152}") long maxBytes,
            ObjectMapper objectMapper) {
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(maxBytes, objectMapper);

        FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/compras/recepcion");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
