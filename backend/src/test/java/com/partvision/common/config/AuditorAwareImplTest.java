package com.partvision.common.config;

import com.partvision.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditorAwareImplTest {

    private final AuditorAwareImpl auditorAware = new AuditorAwareImpl();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacion_devuelveVacio() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void autenticacionNoAutenticada_devuelveVacio() {
        // constructor de 2 args -> authenticated = false
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "pass"));

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void principalNoEsUsuarioAutenticado_devuelveVacio() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void usuarioAutenticado_devuelveSuId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(8L, "admin"), null, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).contains(8L);
    }
}
