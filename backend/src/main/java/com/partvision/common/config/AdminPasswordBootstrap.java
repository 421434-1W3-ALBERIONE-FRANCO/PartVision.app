package com.partvision.common.config;

import com.partvision.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Al arrancar, si la variable de entorno {@code ADMIN_PASSWORD} está definida,
 * fija la contraseña del usuario {@code admin} con ese valor (hasheada con BCrypt).
 * Permite reemplazar la password semilla de desarrollo (débil) en producción sin
 * tocar la base a mano ni versionar ningún hash. Si la variable no está o está
 * vacía, no hace nada (se conserva la password actual).
 */
@Component
@RequiredArgsConstructor
public class AdminPasswordBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordBootstrap.class);
    private static final String ADMIN_USERNAME = "admin";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            return; // sin override: se mantiene la password existente
        }
        usuarioRepository.findByUsername(ADMIN_USERNAME).ifPresentOrElse(admin -> {
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            usuarioRepository.save(admin);
            log.info("Password del usuario '{}' actualizada desde ADMIN_PASSWORD.", ADMIN_USERNAME);
        }, () -> log.warn("ADMIN_PASSWORD definida pero no existe el usuario '{}'.", ADMIN_USERNAME));
    }
}
