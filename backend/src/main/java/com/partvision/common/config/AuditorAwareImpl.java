package com.partvision.common.config;

import com.partvision.common.security.AuthenticatedUser;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Provee el id del usuario autenticado para la auditoria (created_by / updated_by).
 * Si no hay usuario autenticado (procesos internos, endpoints publicos), devuelve vacio.
 */
@Component
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.id());
    }
}
