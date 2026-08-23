package com.partvision.common.config;

import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordBootstrapTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminPasswordBootstrap bootstrap(String password) throws Exception {
        AdminPasswordBootstrap b = new AdminPasswordBootstrap(usuarioRepository, passwordEncoder);
        Field field = AdminPasswordBootstrap.class.getDeclaredField("adminPassword");
        field.setAccessible(true);
        field.set(b, password);
        return b;
    }

    @Test
    void run_conPassword_actualizaAdmin() throws Exception {
        Usuario admin = Usuario.builder().id(1L).username("admin").passwordHash("old").build();
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("nueva-segura")).thenReturn("$2a$hash");

        bootstrap("nueva-segura").run();

        verify(usuarioRepository).save(admin);
    }

    @Test
    void run_sinPassword_noHaceNada() throws Exception {
        bootstrap("").run();

        verify(usuarioRepository, never()).findByUsername("admin");
    }

    @Test
    void run_passwordNull_noHaceNada() throws Exception {
        bootstrap(null).run();

        verify(usuarioRepository, never()).findByUsername("admin");
    }

    @Test
    void run_passwordBlank_noHaceNada() throws Exception {
        bootstrap("   ").run();

        verify(usuarioRepository, never()).findByUsername("admin");
    }

    @Test
    void run_adminNoExiste_noFalla() throws Exception {
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.empty());

        bootstrap("alguna-password").run();

        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
