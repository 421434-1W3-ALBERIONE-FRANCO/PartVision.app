package com.partvision.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Construye la cookie que transporta el JWT en el panel web. Es {@code HttpOnly}
 * (el JavaScript nunca la lee, lo que blinda el token contra robo por XSS) y sus
 * flags {@code Secure}/{@code SameSite} se configuran por entorno:
 * <ul>
 *   <li>dev (http, localhost): {@code secure=false}, o el navegador descarta la cookie;</li>
 *   <li>prod (https, mismo dominio via proxy nginx): {@code secure=true} + {@code SameSite=Strict},
 *       lo mas restrictivo posible (el navegador no manda la cookie en requests cross-site).</li>
 * </ul>
 */
@Component
public class AuthCookieFactory {

    private final String name;
    private final boolean secure;
    private final String sameSite;

    public AuthCookieFactory(
            @Value("${security.cookie.name:pv_token}") String name,
            @Value("${security.cookie.secure:false}") boolean secure,
            @Value("${security.cookie.same-site:Strict}") String sameSite) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /** Nombre de la cookie (lo usa tambien el filtro para leer el token entrante). */
    public String name() {
        return name;
    }

    /** Cookie de sesion con el JWT, con la misma vigencia que el token. */
    public ResponseCookie create(String token, long maxAgeSeconds) {
        return base(token).maxAge(Duration.ofSeconds(maxAgeSeconds)).build();
    }

    /** Cookie vacia y ya expirada: el navegador la borra (logout). */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/");
    }
}
