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

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> recoveryRateLimitFilter(ObjectMapper objectMapper) {
        LoginRateLimitFilter filter =
                new LoginRateLimitFilter(5, Duration.ofMinutes(15), objectMapper);

        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/2fa/recover-request",
                "/api/v1/auth/2fa/recover-confirm");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
