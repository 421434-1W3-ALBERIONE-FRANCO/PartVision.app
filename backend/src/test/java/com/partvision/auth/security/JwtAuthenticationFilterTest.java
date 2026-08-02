package com.partvision.auth.security;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.common.security.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 3_600_000L));
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void doFilter(String authHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authHeader != null) {
            request.addHeader("Authorization", authHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("la cadena debe continuar siempre").isNotNull();
    }

    @Test
    void tokenValido_autenticaConAuthorities() throws Exception {
        Usuario usuario = Usuario.builder()
                .id(3L).username("admin")
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();

        doFilter("Bearer " + jwtService.generateToken(usuario));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        assertThat(((AuthenticatedUser) auth.getPrincipal()).id()).isEqualTo(3L);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void tokenSinRoles_autenticaSinAuthorities() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().subject("u").claim("uid", 1L).signWith(key).compact();

        doFilter("Bearer " + token);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void sinHeader_noAutentica() throws Exception {
        doFilter(null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void headerNoBearer_noAutentica() throws Exception {
        doFilter("Basic dXNlcjpwYXNz");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenInvalido_noAutentica() throws Exception {
        doFilter("Bearer token-corrupto");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
