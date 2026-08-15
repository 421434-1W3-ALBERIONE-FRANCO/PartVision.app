package com.partvision.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Handler de CSRF para una SPA (Angular). Es la receta oficial de Spring Security para
 * apps de una sola pagina que usan {@code CookieCsrfTokenRepository}:
 * <ul>
 *   <li>al <b>escribir</b> el token en la cookie usa proteccion BREACH (XOR), y</li>
 *   <li>al <b>leer</b> el token de la request lo toma crudo del header {@code X-XSRF-TOKEN}
 *       (que Angular manda copiando el valor de la cookie {@code XSRF-TOKEN}).</li>
 * </ul>
 * Sin esto, el valor XOR de la cookie no coincidiria con el valor plano del header y todo
 * POST/PUT/DELETE del panel daria 403.
 */
final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegate = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegate.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // Si viene por header (caso SPA), tomar el valor plano; si viene por parametro, delegar (XOR).
        if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
            return super.resolveCsrfTokenValue(request, csrfToken);
        }
        return this.delegate.resolveCsrfTokenValue(request, csrfToken);
    }
}
