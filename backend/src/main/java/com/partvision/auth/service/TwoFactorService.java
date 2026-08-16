package com.partvision.auth.service;

import com.partvision.auth.domain.TotpRecoveryCode;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.TotpSetupResponse;
import com.partvision.auth.repository.TotpRecoveryCodeRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 2FA por TOTP. Maneja el enrolamiento (generar secret + QR), la activacion (confirmar el
 * primer codigo + emitir codigos de recuperacion), la verificacion en el login (codigo del
 * authenticator o un codigo de recuperacion de un solo uso) y la desactivacion.
 */
@Service
public class TwoFactorService {

    private static final String ISSUER = "PartVision";
    private static final int CANTIDAD_RECOVERY = 10;

    private final UsuarioRepository usuarioRepository;
    private final TotpRecoveryCodeRepository recoveryRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final RecoveryCodeGenerator recoveryGenerator = new RecoveryCodeGenerator();
    private final CodeVerifier codeVerifier;

    public TwoFactorService(UsuarioRepository usuarioRepository,
                            TotpRecoveryCodeRepository recoveryRepository,
                            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.recoveryRepository = recoveryRepository;
        this.passwordEncoder = passwordEncoder;

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // Tolera 1 periodo (±30s) de desfasaje de reloj entre el server y el telefono.
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /** Genera un secret pendiente (no activa aun) y devuelve el otpauth:// para el QR. */
    @Transactional
    public TotpSetupResponse iniciarSetup(Long usuarioId) {
        Usuario usuario = getUsuario(usuarioId);
        String secret = secretGenerator.generate();
        usuario.setTotpSecret(secret);
        usuario.setTotpEnabled(false);
        usuarioRepository.save(usuario);

        String uri = new QrData.Builder()
                .label(usuario.getUsername())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();
        return new TotpSetupResponse(secret, uri);
    }

    /**
     * Confirma el enrolamiento verificando el primer codigo del authenticator. Si es valido,
     * activa el 2FA y (re)genera los codigos de recuperacion, que se devuelven UNA sola vez.
     */
    @Transactional
    public List<String> activar(Long usuarioId, String codigo) {
        Usuario usuario = getUsuario(usuarioId);
        if (usuario.getTotpSecret() == null) {
            throw new BusinessException("Primero iniciá la configuración de 2FA");
        }
        if (!codeVerifier.isValidCode(usuario.getTotpSecret(), codigo)) {
            throw new BusinessException("Código inválido");
        }
        usuario.setTotpEnabled(true);
        usuarioRepository.save(usuario);
        return regenerarRecoveryCodes(usuario);
    }

    /** Verifica un código de login: primero TOTP, y si no, un código de recuperación (que consume). */
    @Transactional
    public boolean verificarLogin(Usuario usuario, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        if (usuario.getTotpSecret() != null && codeVerifier.isValidCode(usuario.getTotpSecret(), codigo)) {
            return true;
        }
        for (TotpRecoveryCode rc : recoveryRepository.findByUsuarioIdAndUsadoFalse(usuario.getId())) {
            if (passwordEncoder.matches(codigo, rc.getCodeHash())) {
                rc.setUsado(true);
                recoveryRepository.save(rc);
                return true;
            }
        }
        return false;
    }

    /** Si el usuario tiene el 2FA activo. */
    @Transactional(readOnly = true)
    public boolean estaActivo(Long usuarioId) {
        return getUsuario(usuarioId).isTotpEnabled();
    }

    /**
     * Desactiva el 2FA. Exige un código válido (TOTP o de recuperación) para que una sesión
     * robada no pueda apagar el segundo factor: borra el secret y los códigos de recuperación.
     */
    @Transactional
    public void desactivar(Long usuarioId, String codigo) {
        Usuario usuario = getUsuario(usuarioId);
        if (!usuario.isTotpEnabled()) {
            return;
        }
        if (!verificarLogin(usuario, codigo)) {
            throw new BusinessException("Código inválido");
        }
        usuario.setTotpSecret(null);
        usuario.setTotpEnabled(false);
        usuarioRepository.save(usuario);
        recoveryRepository.deleteByUsuarioId(usuarioId);
    }

    private List<String> regenerarRecoveryCodes(Usuario usuario) {
        recoveryRepository.deleteByUsuarioId(usuario.getId());
        String[] codes = recoveryGenerator.generateCodes(CANTIDAD_RECOVERY);
        for (String code : codes) {
            recoveryRepository.save(TotpRecoveryCode.builder()
                    .usuario(usuario)
                    .codeHash(passwordEncoder.encode(code))
                    .usado(false)
                    .build());
        }
        return List.of(codes);
    }

    private Usuario getUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
    }
}
