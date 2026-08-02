package com.partvision.auth.security;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 3_600_000L));
    }

    @Test
    void generaYParseaTokenConClaims() {
        Usuario usuario = Usuario.builder()
                .id(7L)
                .username("operario1")
                .roles(Set.of(Rol.builder().id(2L).nombre("OPERARIO").build()))
                .build();

        String token = jwtService.generateToken(usuario);
        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("operario1");
        assertThat(claims.get("uid", Long.class)).isEqualTo(7L);
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("OPERARIO");
        assertThat(jwtService.getExpirationMs()).isEqualTo(3_600_000L);
    }

    @Test
    void tokenInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> jwtService.parse("no-es-un-token"))
                .isInstanceOf(JwtException.class);
    }
}
