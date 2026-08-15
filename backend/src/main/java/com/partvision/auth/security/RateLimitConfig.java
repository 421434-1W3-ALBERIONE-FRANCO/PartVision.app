package com.partvision.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * Registra el {@link LoginRateLimitFilter} scopeado exclusivamente a {@code POST /api/v1/auth/login}
 * y con la maxima prioridad (corre antes de la cadena de seguridad: rechaza los excesos barato,
 * sin tocar la DB). Configurable por entorno:
 * {@code security.rate-limit.login.capacity} (intentos) y {@code refill-minutes} (ventana).
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter(
            @Value("${security.rate-limit.login.capacity:5}") int capacity,
            @Value("${security.rate-limit.login.refill-minutes:1}") long refillMinutes,
            ObjectMapper objectMapper) {
        LoginRateLimitFilter filter =
                new LoginRateLimitFilter(capacity, Duration.ofMinutes(refillMinutes), objectMapper);

        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/v1/auth/login");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
