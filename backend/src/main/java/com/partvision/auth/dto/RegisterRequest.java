package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "La password debe tener al menos 8 caracteres") String password,
        @NotBlank String nombre
) {
}
