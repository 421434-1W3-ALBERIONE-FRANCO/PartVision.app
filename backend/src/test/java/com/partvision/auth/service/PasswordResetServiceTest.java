package com.partvision.auth.service;

import com.partvision.auth.domain.PasswordResetToken;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.PasswordResetTokenRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, usuarioRepository, passwordEncoder, emailService);
    }

    @Test
    void solicitarReset_emailRegistrado_enviaEmail() {
        Usuario usuario = Usuario.builder().id(1L).username("admin").email("admin@test.com").activo(true).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarReset("admin@test.com");

        verify(tokenRepository).deleteByUsuarioId(1L);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).enviarResetPassword(eq("admin@test.com"), anyString());
    }

    @Test
    void solicitarReset_emailNoRegistrado_noEnviaEmail() {
        when(usuarioRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        service.solicitarReset("nadie@test.com");

        verify(emailService, never()).enviarResetPassword(anyString(), anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void solicitarReset_usuarioInactivo_noEnviaEmail() {
        Usuario usuario = Usuario.builder().id(1L).username("inactivo").email("in@test.com").activo(false).build();
        when(usuarioRepository.findByEmail("in@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarReset("in@test.com");

        verify(emailService, never()).enviarResetPassword(anyString(), anyString());
    }

    @Test
    void resetearPassword_tokenValido_cambiaPassword() {
        String tokenPlano = "test-token-abc";
        String hash = PasswordResetService.sha256(tokenPlano);
        Usuario usuario = Usuario.builder().id(1L).username("admin")
                .passwordHash(passwordEncoder.encode("vieja")).build();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L).tokenHash(hash).usuario(usuario)
                .expiraEn(Instant.now().plus(30, ChronoUnit.MINUTES))
                .usado(false).build();
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(resetToken));

        service.resetearPassword(tokenPlano, "nuevaPassword123");

        assertThat(passwordEncoder.matches("nuevaPassword123", usuario.getPasswordHash())).isTrue();
        assertThat(resetToken.isUsado()).isTrue();
        verify(usuarioRepository).save(usuario);
        verify(tokenRepository).save(resetToken);
    }

    @Test
    void resetearPassword_tokenExpirado_lanzaExcepcion() {
        String tokenPlano = "expired-token";
        String hash = PasswordResetService.sha256(tokenPlano);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L).tokenHash(hash)
                .usuario(Usuario.builder().id(1L).build())
                .expiraEn(Instant.now().minus(1, ChronoUnit.HOURS))
                .usado(false).build();
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.resetearPassword(tokenPlano, "nuevaPassword123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void resetearPassword_tokenYaUsado_lanzaExcepcion() {
        String tokenPlano = "used-token";
        String hash = PasswordResetService.sha256(tokenPlano);
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(1L).tokenHash(hash)
                .usuario(Usuario.builder().id(1L).build())
                .expiraEn(Instant.now().plus(30, ChronoUnit.MINUTES))
                .usado(true).build();
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> service.resetearPassword(tokenPlano, "nuevaPassword123"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void resetearPassword_tokenInexistente_lanzaExcepcion() {
        String tokenPlano = "no-existe";
        when(tokenRepository.findByTokenHash(PasswordResetService.sha256(tokenPlano)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetearPassword(tokenPlano, "nuevaPassword123"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sha256_mismaEntrada_mismoHash() {
        String hash1 = PasswordResetService.sha256("test-input");
        String hash2 = PasswordResetService.sha256("test-input");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void sha256_entradasDistintas_hashesDistintos() {
        String hash1 = PasswordResetService.sha256("input-a");
        String hash2 = PasswordResetService.sha256("input-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
