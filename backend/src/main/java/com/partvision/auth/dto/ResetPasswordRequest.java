package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "El token es obligatorio") String token,
        @NotBlank @Size(min = 8, message = "La password debe tener al menos 8 caracteres") String password
) {}
