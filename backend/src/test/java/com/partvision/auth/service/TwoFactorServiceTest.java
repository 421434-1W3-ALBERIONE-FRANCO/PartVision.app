package com.partvision.auth.service;

import com.partvision.auth.domain.TotpRecoveryCode;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.TotpRecoveryCodeRepository;
import com.partvision.auth.repository.UsuarioRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Genera el código TOTP válido para el instante actual (mismo algoritmo que el verificador). */
    private String codigoActual() throws Exception {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
    }

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
}
