package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de login. El {@code code} es el código 2FA (TOTP o de recuperación); es
 * opcional: solo se envía si el usuario tiene 2FA activo (el backend lo pide con un 401
 * {@code twoFactorRequired} si falta).
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String code
) {
}
