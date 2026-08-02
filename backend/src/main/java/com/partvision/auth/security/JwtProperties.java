package com.partvision.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del JWT. El secret se lee de la configuracion externa
 * (nunca hardcodeado). Debe tener al menos 32 bytes para HS256.
 *
 * @param secret       clave de firma HMAC
 * @param expirationMs vigencia del token en milisegundos
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long expirationMs) {
}
