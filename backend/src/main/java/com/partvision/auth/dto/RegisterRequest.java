package com.partvision.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "La password debe tener al menos 8 caracteres") String password,
        @NotBlank String nombre,
        /** Rol a asignar (ADMIN u OPERARIO). Opcional: si es null se usa OPERARIO. */
        String rol
) {
    /** Constructor de compatibilidad: sin rol explicito -> OPERARIO (no rompe llamadas/tests previos). */
    public RegisterRequest(String username, String password, String nombre) {
        this(username, password, nombre, null);
    }
}
