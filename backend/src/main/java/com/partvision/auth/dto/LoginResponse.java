package com.partvision.auth.dto;

/**
 * Respuesta del login. Nunca incluye el objeto usuario ni el password.
 *
 * @param token     JWT firmado
 * @param expiresIn vigencia del token en segundos
 */
public record LoginResponse(String token, long expiresIn) {
}
