package com.partvision.auth.service;

import com.partvision.auth.domain.TwoFactorRecoveryToken;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.TotpRecoveryCodeRepository;
import com.partvision.auth.repository.TwoFactorRecoveryTokenRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class TwoFactorRecoveryServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TotpRecoveryCodeRepository recoveryCodeRepository;
    @Mock
    private TwoFactorRecoveryTokenRepository recoveryTokenRepository;
    @Mock
    private EmailService emailService;

    private TwoFactorRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new TwoFactorRecoveryService(
                usuarioRepository, recoveryCodeRepository, recoveryTokenRepository, emailService);
    }

    @Test
    void solicitarRecuperacion_usuarioCon2fa_guardaTokenYEnviaEmail() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin").email("admin@test.com")
                .activo(true).totpEnabled(true).totpSecret("secret")
                .build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarRecuperacion("admin@test.com");

        verify(recoveryTokenRepository).deleteByUsuarioId(1L);
        verify(recoveryTokenRepository).save(any(TwoFactorRecoveryToken.class));
        verify(emailService).enviarRecuperacion2FA(eq("admin@test.com"), anyString());
    }

    @Test
    void solicitarRecuperacion_usuarioSin2fa_noEnviaEmail() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("user").email("user@test.com")
                .activo(true).totpEnabled(false)
                .build();
        when(usuarioRepository.findByEmail("user@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarRecuperacion("user@test.com");

        verify(emailService, never()).enviarRecuperacion2FA(anyString(), anyString());
        verify(recoveryTokenRepository, never()).save(any());
    }

    @Test
    void solicitarRecuperacion_emailInexistente_noEnviaEmail() {
        when(usuarioRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        service.solicitarRecuperacion("nadie@test.com");

        verify(emailService, never()).enviarRecuperacion2FA(anyString(), anyString());
    }

    @Test
    void confirmarRecuperacion_codigoValido_desactiva2fa() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin").email("admin@test.com")
                .activo(true).totpEnabled(true).totpSecret("secret")
                .build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));

        service.solicitarRecuperacion("admin@test.com");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacion2FA(eq("admin@test.com"), codeCaptor.capture());
        String codigoPlano = codeCaptor.getValue();

        ArgumentCaptor<TwoFactorRecoveryToken> tokenCaptor =
                ArgumentCaptor.forClass(TwoFactorRecoveryToken.class);
        verify(recoveryTokenRepository).save(tokenCaptor.capture());
        TwoFactorRecoveryToken tokenGuardado = tokenCaptor.getValue();

        when(recoveryTokenRepository.findByUsuarioIdAndUsadoFalse(1L))
                .thenReturn(Optional.of(tokenGuardado));

        service.confirmarRecuperacion("admin@test.com", codigoPlano);

        assertThat(usuario.getTotpSecret()).isNull();
        assertThat(usuario.isTotpEnabled()).isFalse();
        assertThat(tokenGuardado.isUsado()).isTrue();
        verify(usuarioRepository).save(usuario);
        verify(recoveryCodeRepository).deleteByUsuarioId(1L);
    }

    @Test
    void confirmarRecuperacion_codigoIncorrecto_lanzaExcepcion() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin").email("admin@test.com")
                .activo(true).totpEnabled(true).totpSecret("secret")
                .build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));

        TwoFactorRecoveryToken token = TwoFactorRecoveryToken.builder()
                .id(1L).codeHash(PasswordResetService.sha256("123456")).usuario(usuario)
                .expiraEn(Instant.now().plus(15, ChronoUnit.MINUTES)).usado(false).build();
        when(recoveryTokenRepository.findByUsuarioIdAndUsadoFalse(1L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmarRecuperacion("admin@test.com", "999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido o expirado");
    }

    @Test
    void confirmarRecuperacion_sinSolicitudPrevia_lanzaExcepcion() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin").email("admin@test.com")
                .activo(true).totpEnabled(true).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));
        when(recoveryTokenRepository.findByUsuarioIdAndUsadoFalse(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmarRecuperacion("admin@test.com", "123456"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void confirmarRecuperacion_tokenExpirado_lanzaExcepcion() {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin").email("admin@test.com")
                .activo(true).totpEnabled(true).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(usuario));

        TwoFactorRecoveryToken token = TwoFactorRecoveryToken.builder()
                .id(1L).codeHash(PasswordResetService.sha256("123456")).usuario(usuario)
                .expiraEn(Instant.now().minus(1, ChronoUnit.HOURS)).usado(false).build();
        when(recoveryTokenRepository.findByUsuarioIdAndUsadoFalse(1L))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirmarRecuperacion("admin@test.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido o expirado");
    }
}
