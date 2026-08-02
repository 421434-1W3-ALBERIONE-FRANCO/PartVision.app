package com.partvision.auth.service;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.InvalidCredentialsException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private UsuarioService usuarioService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void getCurrentUser_devuelveElUsuarioAutenticado() {
        autenticarComo(new AuthenticatedUser(5L, "admin"));
        Usuario usuario = Usuario.builder()
                .id(5L).username("admin").nombre("Admin").activo(true)
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        UsuarioResponse response = usuarioService.getCurrentUser();

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.roles()).containsExactly("ADMIN");
    }

    @Test
    void getCurrentUser_sinAutenticacion_lanza401() {
        assertThatThrownBy(() -> usuarioService.getCurrentUser())
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getCurrentUser_principalNoEsUsuarioAutenticado_lanza401() {
        autenticarComo("anonymousUser");

        assertThatThrownBy(() -> usuarioService.getCurrentUser())
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getCurrentUser_usuarioNoExiste_lanza404() {
        autenticarComo(new AuthenticatedUser(99L, "fantasma"));
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.getCurrentUser())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
