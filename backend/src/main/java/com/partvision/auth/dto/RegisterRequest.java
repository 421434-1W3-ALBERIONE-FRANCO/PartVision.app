package com.partvision.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "La password debe tener al menos 8 caracteres") String password,
        @NotBlank String nombre,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        String email,
        String rol
) {
    public RegisterRequest(String username, String password, String nombre) {
        this(username, password, nombre, null, null);
    }

    public RegisterRequest(String username, String password, String nombre, String email) {
        this(username, password, nombre, email, null);
    }
}
