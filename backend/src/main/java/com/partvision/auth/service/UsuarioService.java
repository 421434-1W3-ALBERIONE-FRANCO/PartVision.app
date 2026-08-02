package com.partvision.auth.service;

import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.InvalidCredentialsException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Devuelve el usuario autenticado. La identidad se toma del SecurityContext
     * (nunca de un parametro del cliente), respetando el ownership en el service.
     */
    @Transactional(readOnly = true)
    public UsuarioResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        Usuario usuario = usuarioRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", principal.id()));
        return UsuarioResponse.from(usuario);
    }
}
