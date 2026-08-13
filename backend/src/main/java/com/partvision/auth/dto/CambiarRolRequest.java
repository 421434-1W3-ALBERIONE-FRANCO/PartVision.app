package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cambio de rol de un usuario existente (ADMIN u OPERARIO). */
public record CambiarRolRequest(
        @NotBlank String rol
) {
}
