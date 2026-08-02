package com.partvision.common.security;

/**
 * Principal minimo del usuario autenticado, reconstruido desde el JWT en cada
 * request. Vive en {@code common} para que la auditoria pueda leerlo sin
 * depender del modulo de auth.
 */
public record AuthenticatedUser(Long id, String username) {
}
