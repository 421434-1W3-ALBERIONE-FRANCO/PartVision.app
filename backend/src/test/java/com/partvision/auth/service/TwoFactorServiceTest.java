package com.partvision.auth.service;

import com.partvision.auth.domain.TotpRecoveryCode;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.TotpSetupResponse;
import com.partvision.auth.repository.TotpRecoveryCodeRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TotpRecoveryCodeRepository recoveryRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private TwoFactorService service;
    private String secret;

    @BeforeEach
    void setUp() {
        service = new TwoFactorService(usuarioRepository, recoveryRepository, passwordEncoder);
        secret = new DefaultSecretGenerator().generate();
    }

    /** Genera el codigo TOTP valido para el instante actual (mismo algoritmo que el verificador). */
    private String codigoActual() throws Exception {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
    }

    // ── iniciarSetup ────────────────────────────────────────────────────

    @Test
    void iniciarSetup_generaSecretYUri() {
        Usuario usuario = Usuario.builder().id(1L).username("admin").build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        TotpSetupResponse response = service.iniciarSetup(1L);

        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpauthUri()).contains("otpauth://totp/");
        assertThat(response.otpauthUri()).contains("admin");
        assertThat(response.otpauthUri()).contains("PartVision");
    }

    @Test
    void iniciarSetup_guardaSecretEnUsuarioYDeshabilitaTotp() {
        Usuario usuario = Usuario.builder().id(1L).username("admin").totpEnabled(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.iniciarSetup(1L);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario saved = captor.getValue();
        assertThat(saved.getTotpSecret()).isNotBlank();
        assertThat(saved.isTotpEnabled()).isFalse();
    }

    @Test
    void iniciarSetup_usuarioInexistente_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.iniciarSetup(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario");
    }

    // ── activar ─────────────────────────────────────────────────────────

    @Test
    void activar_conCodigoValido_habilitaTotpYDevuelveRecoveryCodes() throws Exception {
        Usuario usuario = Usuario.builder().id(1L).username("admin").totpSecret(secret).totpEnabled(false).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> codes = service.activar(1L, codigoActual());

        assertThat(codes).hasSize(10);
        assertThat(usuario.isTotpEnabled()).isTrue();
        verify(recoveryRepository).deleteByUsuarioId(1L);
        // Se guardan 10 recovery codes hasheados
        verify(recoveryRepository, times(10)).save(any(TotpRecoveryCode.class));
    }

    @Test
    void activar_sinSecretPendiente_lanzaBusinessException() {
        Usuario usuario = Usuario.builder().id(1L).username("admin").totpSecret(null).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.activar(1L, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("configuraci");
    }

    @Test
    void activar_conCodigoInvalido_lanzaBusinessException() {
        Usuario usuario = Usuario.builder().id(1L).username("admin").totpSecret(secret).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.activar(1L, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inv");
    }

    @Test
    void activar_usuarioInexistente_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activar(99L, "123456"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── verificarLogin ──────────────────────────────────────────────────

    @Test
    void verificarLogin_conTotpValido_true() throws Exception {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();

        assertThat(service.verificarLogin(usuario, codigoActual())).isTrue();
    }

    @Test
    void verificarLogin_conCodigoIncorrecto_false() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of());

        assertThat(service.verificarLogin(usuario, "000000")).isFalse();
    }

    @Test
    void verificarLogin_conRecoveryCode_loConsume() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        TotpRecoveryCode rc = TotpRecoveryCode.builder()
                .usuario(usuario).codeHash(passwordEncoder.encode("REC-123")).usado(false).build();
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of(rc));

        boolean ok = service.verificarLogin(usuario, "REC-123");

        assertThat(ok).isTrue();
        assertThat(rc.isUsado()).isTrue();
        verify(recoveryRepository).save(rc);
    }

    @Test
    void verificarLogin_codigoNuloOVacio_false() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();

        assertThat(service.verificarLogin(usuario, null)).isFalse();
        assertThat(service.verificarLogin(usuario, "  ")).isFalse();
    }

    @Test
    void verificarLogin_sinTotpSecretYSinRecovery_false() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(null).totpEnabled(false).build();
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of());

        assertThat(service.verificarLogin(usuario, "123456")).isFalse();
    }

    @Test
    void verificarLogin_recoveryCodeNoCoincide_false() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        TotpRecoveryCode rc = TotpRecoveryCode.builder()
                .usuario(usuario).codeHash(passwordEncoder.encode("REAL-CODE")).usado(false).build();
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of(rc));

        assertThat(service.verificarLogin(usuario, "WRONG-CODE")).isFalse();
        assertThat(rc.isUsado()).isFalse();
        verify(recoveryRepository, never()).save(any());
    }

    @Test
    void verificarLogin_multipleRecoveryCodes_coincideConSegundo() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        TotpRecoveryCode rc1 = TotpRecoveryCode.builder()
                .usuario(usuario).codeHash(passwordEncoder.encode("CODE-AAA")).usado(false).build();
        TotpRecoveryCode rc2 = TotpRecoveryCode.builder()
                .usuario(usuario).codeHash(passwordEncoder.encode("CODE-BBB")).usado(false).build();
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of(rc1, rc2));

        boolean ok = service.verificarLogin(usuario, "CODE-BBB");

        assertThat(ok).isTrue();
        assertThat(rc1.isUsado()).isFalse();
        assertThat(rc2.isUsado()).isTrue();
        verify(recoveryRepository).save(rc2);
    }

    // ── estaActivo ──────────────────────────────────────────────────────

    @Test
    void estaActivo_conTotpHabilitado_true() {
        Usuario usuario = Usuario.builder().id(1L).totpEnabled(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(service.estaActivo(1L)).isTrue();
    }

    @Test
    void estaActivo_conTotpDeshabilitado_false() {
        Usuario usuario = Usuario.builder().id(1L).totpEnabled(false).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThat(service.estaActivo(1L)).isFalse();
    }

    @Test
    void estaActivo_usuarioInexistente_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.estaActivo(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── desactivar ──────────────────────────────────────────────────────

    @Test
    void desactivar_conTotpNoHabilitado_noHaceNada() {
        Usuario usuario = Usuario.builder().id(1L).totpEnabled(false).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        service.desactivar(1L, "cualquier-codigo");

        // No guarda ni borra nada: early return
        verify(usuarioRepository, never()).save(any());
        verify(recoveryRepository, never()).deleteByUsuarioId(any());
    }

    @Test
    void desactivar_conCodigoTotpValido_limpiaSecretYRecoveryCodes() throws Exception {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.desactivar(1L, codigoActual());

        assertThat(usuario.getTotpSecret()).isNull();
        assertThat(usuario.isTotpEnabled()).isFalse();
        verify(usuarioRepository).save(usuario);
        verify(recoveryRepository).deleteByUsuarioId(1L);
    }

    @Test
    void desactivar_conRecoveryCode_limpiaSecretYRecoveryCodes() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        TotpRecoveryCode rc = TotpRecoveryCode.builder()
                .usuario(usuario).codeHash(passwordEncoder.encode("REC-DISABLE")).usado(false).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of(rc));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.desactivar(1L, "REC-DISABLE");

        assertThat(usuario.getTotpSecret()).isNull();
        assertThat(usuario.isTotpEnabled()).isFalse();
        verify(recoveryRepository).deleteByUsuarioId(1L);
    }

    @Test
    void desactivar_conCodigoInvalido_lanzaBusinessException() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(recoveryRepository.findByUsuarioIdAndUsadoFalse(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.desactivar(1L, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inv");
    }

    @Test
    void desactivar_usuarioInexistente_lanza404() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar(99L, "123456"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void desactivar_conCodigoNulo_lanzaBusinessException() {
        Usuario usuario = Usuario.builder().id(1L).totpSecret(secret).totpEnabled(true).build();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.desactivar(1L, null))
                .isInstanceOf(BusinessException.class);
    }
}
