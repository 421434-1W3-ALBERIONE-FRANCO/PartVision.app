package com.partvision.auth.security;

import com.partvision.common.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Valida el JWT en cada request y, si es valido, coloca el usuario autenticado en el
 * SecurityContext. Acepta el token por dos vias:
 * <ul>
 *   <li>cookie {@code HttpOnly} (el panel web; el navegador la manda solo) — via principal;</li>
 *   <li>header {@code Authorization: Bearer <token>} — para Swagger, tests y herramientas.</li>
 * </ul>
 * Si no hay token o es invalido, deja pasar sin autenticar (el
 * {@link RestAuthenticationEntryPoint} respondera 401 en endpoints protegidos).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   @Value("${security.cookie.name:pv_token}") String cookieName) {
        this.jwtService = jwtService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    /** Prioriza el header (herramientas/tests); si no hay, cae a la cookie (panel web). */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> cookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token);
            Long uid = claims.get("uid", Long.class);
            List<?> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = (roles == null ? List.of() : roles).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            AuthenticatedUser principal = new AuthenticatedUser(uid, claims.getSubject());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
