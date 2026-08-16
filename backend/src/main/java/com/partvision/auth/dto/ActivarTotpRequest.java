package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Código del authenticator (o de recuperación) para activar o desactivar el 2FA. */
public record ActivarTotpRequest(@NotBlank String code) {
}
