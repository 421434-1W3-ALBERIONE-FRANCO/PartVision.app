package com.partvision.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Fuerza que el token CSRF se materialice en la respuesta. {@code CookieCsrfTokenRepository}
 * es "diferido": la cookie {@code XSRF-TOKEN} solo se emite cuando alguien lee el token.
 * Al invocar {@code getToken()} en cada request, garantizamos que la SPA reciba la cookie
 * y pueda mandar el header en el siguiente POST/PUT/DELETE.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
