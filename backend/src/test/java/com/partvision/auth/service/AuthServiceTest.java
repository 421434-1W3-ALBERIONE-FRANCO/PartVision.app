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
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.common.exception.TwoFactorRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private TwoFactorService twoFactorService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, rolRepository, passwordEncoder, jwtService, twoFactorService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private Rol rolOperario() {
        return Rol.builder().id(2L).nombre("OPERARIO").build();
    }

    private Rol rolAdmin() {
        return Rol.builder().id(1L).nombre("ADMIN").build();
    }

    private void stubSaveReturnsWithId(long id) {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(id);
            return u;
        });
    }

    // =====================================================================
    // register / crearUsuario
    // =====================================================================

    @Test
    void register_creaUsuarioConRolOperarioYPasswordHasheada() {
        when(usuarioRepository.existsByUsername("nuevo")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.of(rolOperario()));
        stubSaveReturnsWithId(10L);

        UsuarioResponse response = authService.register(new RegisterRequest("nuevo", "password123", "Juan"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("nuevo");
        assertThat(response.roles()).containsExactly("OPERARIO");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
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
    void crearUsuario_conEmailValido_guardaEmail() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(usuarioRepository.existsByEmail("usr@mail.com")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.of(rolOperario()));
        stubSaveReturnsWithId(11L);

        UsuarioResponse response = authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", "usr@mail.com"));

        assertThat(response.email()).isEqualTo("usr@mail.com");
        assertThat(response.id()).isEqualTo(11L);
    }

    @Test
    void crearUsuario_emailDuplicado_lanza409() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(usuarioRepository.existsByEmail("dup@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", "dup@mail.com")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void crearUsuario_emailNull_guardaNull() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.of(rolOperario()));
        stubSaveReturnsWithId(12L);

        UsuarioResponse response = authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", null));

        assertThat(response.email()).isNull();
    }

    @Test
    void crearUsuario_emailBlanco_guardaNull() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.of(rolOperario()));
        stubSaveReturnsWithId(13L);

        UsuarioResponse response = authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", "   "));

        assertThat(response.email()).isNull();
    }

    @Test
    void crearUsuario_conRolExplicito_usaRolIndicado() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin()));
        stubSaveReturnsWithId(14L);

        UsuarioResponse response = authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", null, "admin"));

        assertThat(response.roles()).containsExactly("ADMIN");
    }

    @Test
    void crearUsuario_conRolBlanco_usaRolPorDefecto() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(rolRepository.findByNombre("OPERARIO")).thenReturn(Optional.of(rolOperario()));
        stubSaveReturnsWithId(15L);

        UsuarioResponse response = authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", null, "   "));

        assertThat(response.roles()).containsExactly("OPERARIO");
    }

    @Test
    void crearUsuario_rolInvalido_lanzaBusinessException() {
        when(usuarioRepository.existsByUsername("usr")).thenReturn(false);
        when(rolRepository.findByNombre("FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.crearUsuario(
                new RegisterRequest("usr", "password123", "Nombre", null, "fantasma")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FANTASMA");
    }

    // =====================================================================
    // listarTodos
    // =====================================================================

    @Test
    void listarTodos_devuelveListaDeUsuarioResponse() {
        Usuario u1 = Usuario.builder().id(1L).username("admin").nombre("Admin").activo(true)
                .roles(Set.of(rolAdmin())).build();
        Usuario u2 = Usuario.builder().id(2L).username("operario").nombre("Operario").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UsuarioResponse> result = authService.listarTodos();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UsuarioResponse::username).containsExactly("admin", "operario");
    }

    @Test
    void listarTodos_sinUsuarios_devuelveListaVacia() {
        when(usuarioRepository.findAll()).thenReturn(List.of());

        assertThat(authService.listarTodos()).isEmpty();
    }

    // =====================================================================
    // cambiarRol
    // =====================================================================

    @Test
    void cambiarRol_exitoso_reemplazaRoles() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.cambiarRol(1L, "admin");

        assertThat(response.roles()).containsExactly("ADMIN");
    }

    @Test
    void cambiarRol_usuarioNoExiste_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarRol(99L, "ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cambiarRol_rolInvalido_lanzaBusinessException() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByNombre("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarRol(1L, "inexistente"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cambiarRol_rolNull_buscaStringVacio() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByNombre("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarRol(1L, null))
                .isInstanceOf(BusinessException.class);
    }

    // =====================================================================
    // eliminarUsuario
    // =====================================================================

    @Test
    void eliminarUsuario_exitoso_eliminaDelRepositorio() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        authService.eliminarUsuario(1L);

        verify(usuarioRepository).delete(usuario);
    }

    @Test
    void eliminarUsuario_noExiste_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.eliminarUsuario(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================================
    // toggleActivo
    // =====================================================================

    @Test
    void toggleActivo_activoAInactivo() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.toggleActivo(1L);

        assertThat(response.activo()).isFalse();
    }

    @Test
    void toggleActivo_inactivoAActivo() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(false)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.toggleActivo(1L);

        assertThat(response.activo()).isTrue();
    }

    @Test
    void toggleActivo_noExiste_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.toggleActivo(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================================
    // cambiarEmail
    // =====================================================================

    @Test
    void cambiarEmail_conEmailNuevoValido_loGuarda() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmail("new@mail.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.cambiarEmail(1L, "new@mail.com");

        assertThat(response.email()).isEqualTo("new@mail.com");
    }

    @Test
    void cambiarEmail_emailYaUsadoPorOtroUsuario_lanza409() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").activo(true)
                .roles(Set.of(rolOperario())).build();
        Usuario otro = Usuario.builder().id(2L).username("otro").nombre("Otro").activo(true)
                .roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmail("taken@mail.com")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> authService.cambiarEmail(1L, "taken@mail.com"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void cambiarEmail_emailYaUsadoPorMismoUsuario_ok() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").email("same@mail.com")
                .activo(true).roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByEmail("same@mail.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.cambiarEmail(1L, "same@mail.com");

        assertThat(response.email()).isEqualTo("same@mail.com");
    }

    @Test
    void cambiarEmail_null_limpiaEmail() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").email("old@mail.com")
                .activo(true).roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.cambiarEmail(1L, null);

        assertThat(response.email()).isNull();
    }

    @Test
    void cambiarEmail_blank_limpiaEmail() {
        Usuario usuario = Usuario.builder().id(1L).username("usr").nombre("N").email("old@mail.com")
                .activo(true).roles(Set.of(rolOperario())).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse response = authService.cambiarEmail(1L, "   ");

        assertThat(response.email()).isNull();
    }

    @Test
    void cambiarEmail_noExiste_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarEmail(99L, "x@x.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================================
    // cambiarPasswordPropia
    // =====================================================================

    @Test
    void cambiarPasswordPropia_exitoSin2fa() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.cambiarPasswordPropia(1L, "actual123", "nueva456!", null);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(passwordEncoder.matches("nueva456!", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void cambiarPasswordPropia_passwordActualIncorrecta_lanza401() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.cambiarPasswordPropia(1L, "incorrecta", "nueva456!", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void cambiarPasswordPropia_noExiste_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.cambiarPasswordPropia(99L, "x", "y", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cambiarPasswordPropia_con2faSinCodigo_lanzaBusinessException() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.cambiarPasswordPropia(1L, "actual123", "nueva456!", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2FA");
    }

    @Test
    void cambiarPasswordPropia_con2faCodigoBlanco_lanzaBusinessException() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.cambiarPasswordPropia(1L, "actual123", "nueva456!", "   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cambiarPasswordPropia_con2faCodigoInvalido_lanza401() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(twoFactorService.verificarLogin(usuario, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.cambiarPasswordPropia(1L, "actual123", "nueva456!", "000000"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void cambiarPasswordPropia_con2faCodigoValido_cambiaPassword() {
        Usuario usuario = usuarioActivoConPassword("actual123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(twoFactorService.verificarLogin(usuario, "123456")).thenReturn(true);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.cambiarPasswordPropia(1L, "actual123", "nueva456!", "123456");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(passwordEncoder.matches("nueva456!", captor.getValue().getPasswordHash())).isTrue();
    }

    // =====================================================================
    // login
    // =====================================================================

    @Test
    void login_conCredencialesValidas_devuelveToken() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("token-jwt");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        LoginResponse response = authService.login(new LoginRequest("admin", "secreta123", null));

        assertThat(response.token()).isEqualTo("token-jwt");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
    }

    @Test
    void login_usuarioInexistente_lanza401() {
        when(usuarioRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie", "x", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_usuarioInactivo_lanza401() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setActivo(false);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secreta123", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_passwordIncorrecta_lanza401() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "incorrecta", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_con2faSinCodigo_pideSegundoFactor() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secreta123", null)))
                .isInstanceOf(TwoFactorRequiredException.class);
    }

    @Test
    void login_con2faCodigoBlanco_pideSegundoFactor() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secreta123", "   ")))
                .isInstanceOf(TwoFactorRequiredException.class);
    }

    @Test
    void login_con2faCodigoInvalido_lanza401() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        when(twoFactorService.verificarLogin(usuario, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "secreta123", "000000")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_con2faCodigoValido_devuelveToken() {
        Usuario usuario = usuarioActivoConPassword("secreta123");
        usuario.setTotpEnabled(true);
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
        when(twoFactorService.verificarLogin(usuario, "123456")).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("token-jwt");
        when(jwtService.getExpirationMs()).thenReturn(3_600_000L);

        LoginResponse response = authService.login(new LoginRequest("admin", "secreta123", "123456"));

        assertThat(response.token()).isEqualTo("token-jwt");
    }
}
