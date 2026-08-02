package com.partvision.auth.dto;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vista publica de un usuario. Nunca expone el password.
 */
public record UsuarioResponse(
        Long id,
        String username,
        String nombre,
        boolean activo,
        Set<String> roles
) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.isActivo(),
                usuario.getRoles().stream().map(Rol::getNombre).collect(Collectors.toSet()));
    }
}
