package com.partvision.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void corsConfigurationSource_permiteElOrigenConfigurado() {
        SecurityConfig config = new SecurityConfig(null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("http://localhost:4200"));

        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/productos");
        CorsConfiguration cfg = source.getCorsConfiguration(request);

        assertThat(cfg).isNotNull();
        assertThat(cfg.getAllowedOrigins()).containsExactly("http://localhost:4200");
        assertThat(cfg.getAllowedMethods()).contains("POST", "GET", "DELETE");
    }
}
