package com.partvision.auth.controller;

import com.partvision.auth.dto.CambiarRolRequest;
import com.partvision.auth.dto.RegisterRequest;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.UsuarioService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestión de usuarios. Los endpoints de administración requieren rol ADMIN.
 * El endpoint /me es accesible por cualquier usuario autenticado.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final AuthService authService;

    /** Devuelve el perfil del usuario actualmente autenticado. */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me() {
        return ResponseEntity.ok(usuarioService.getCurrentUser());
    }

    /** Lista todos los usuarios del sistema (solo ADMIN). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(authService.listarTodos());
    }

    /**
     * Crea un nuevo usuario con el rol indicado (ADMIN u OPERARIO; default OPERARIO).
     * Solo ADMIN.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.crearUsuario(request));
    }

    /**
     * Activa o desactiva un usuario (toggle). Un admin no puede desactivarse a sí mismo.
     */
    @PatchMapping("/{id}/activo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> toggleActivo(@PathVariable Long id, Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedUser principal
                && principal.id().equals(id)) {
            throw new BusinessException("No podés desactivar tu propia cuenta");
        }
        return ResponseEntity.ok(authService.toggleActivo(id));
    }

    /**
     * Cambia el rol de un usuario (ADMIN u OPERARIO). Un admin no puede cambiarse
     * su propio rol (evita auto-degradarse y quedar sin acceso admin).
     */
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> cambiarRol(@PathVariable Long id,
            @Valid @RequestBody CambiarRolRequest request, Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedUser principal
                && principal.id().equals(id)) {
            throw new BusinessException("No podés cambiar tu propio rol");
        }
        return ResponseEntity.ok(authService.cambiarRol(id, request.rol()));
    }
}
