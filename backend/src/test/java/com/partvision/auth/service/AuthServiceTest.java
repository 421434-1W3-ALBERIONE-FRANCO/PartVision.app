package com.partvision.auth.service;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.LoginRequest;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.dto.RegisterRequest;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.repository.RolRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.auth.security.JwtService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.InvalidCredentialsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, rolRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_creaUsuarioConRolOperarioYPasswordHasheada() {
        when(usuarioRepository.existsByUsername("nuevo")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO"))
                .thenReturn(Optional.of(Rol.builder().id(2L).nombre("OPERARIO").build()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UsuarioResponse response = authService.register(new RegisterRequest("nuevo", "password123", "Juan"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("nuevo");
        assertThat(response.roles()).containsExactly("OPERARIO");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        org.mockito.Mockito.verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void register_usernameDuplicado_lanza409() {
        when(usuarioRepository.existsByUsername("existente")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("existente", "password123", "Ana")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void register_sinRolPorDefecto_lanzaBusinessException() {
        when(usuarioRepository.existsByUsername("nuevo")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(new RegisterRequest("nuevo", "password123", "Juan")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_conCredencialesValidas_devuelveToken() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("token-jwt");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        LoginResponse response = authService.login(new LoginRequest("admin", "secreta123"));

        assertThat(response.token()).isEqualTo("token-jwt");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
    }

    @Test
    void login_usuarioInexistente_lanza401() {
        when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_usuarioInactivo_lanza401() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setActivo(false);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secreta123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_passwordIncorrecta_lanza401() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "incorrecta")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    private Usuario usuarioActivoConPassword(String rawPassword) {
        return Usuario.builder()
                .id(1L)
                .username("admin")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .nombre("Admin")
                .activo(true)
                .roles(Set.of(Rol.builder().id(1L).nombre("ADMIN").build()))
                .build();
    }
}
