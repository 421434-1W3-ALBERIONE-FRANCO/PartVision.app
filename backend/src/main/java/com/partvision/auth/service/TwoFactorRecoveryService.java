package com.partvision.auth.service;

import com.partvision.auth.domain.TwoFactorRecoveryToken;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.TotpRecoveryCodeRepository;
import com.partvision.auth.repository.TwoFactorRecoveryTokenRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorRecoveryService {

    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int CODE_LENGTH = 6;

    private final UsuarioRepository usuarioRepository;
    private final TotpRecoveryCodeRepository recoveryCodeRepository;
    private final TwoFactorRecoveryTokenRepository recoveryTokenRepository;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void solicitarRecuperacion(String email) {
        Optional<Usuario> opt = usuarioRepository.findByEmail(email);
        if (opt.isEmpty() || !opt.get().isTotpEnabled() || !opt.get().isActivo()) {
            log.debug("Recuperación 2FA solicitada para email sin 2FA activo: {}", email);
            return;
        }
        Usuario usuario = opt.get();

        recoveryTokenRepository.deleteByUsuarioId(usuario.getId());

        String codigoPlano = generarCodigo();
        String hash = PasswordResetService.sha256(codigoPlano);

        TwoFactorRecoveryToken token = TwoFactorRecoveryToken.builder()
                .codeHash(hash)
                .usuario(usuario)
                .expiraEn(Instant.now().plus(CODE_EXPIRATION_MINUTES, ChronoUnit.MINUTES))
                .build();
        recoveryTokenRepository.save(token);

        emailService.enviarRecuperacion2FA(email, codigoPlano);
    }

    @Transactional
    public void confirmarRecuperacion(String email, String codigo) {
        String hash = PasswordResetService.sha256(codigo);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Código inválido o expirado"));

        TwoFactorRecoveryToken token = recoveryTokenRepository
                .findByUsuarioIdAndUsadoFalse(usuario.getId())
                .orElseThrow(() -> new BusinessException("Código inválido o expirado"));

        if (token.estaExpirado() || !MessageDigest.isEqual(
                token.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Código inválido o expirado");
        }

        token.setUsado(true);
        recoveryTokenRepository.save(token);

        usuario.setTotpSecret(null);
        usuario.setTotpEnabled(false);
        usuarioRepository.save(usuario);
        recoveryCodeRepository.deleteByUsuarioId(usuario.getId());

        log.info("2FA desactivado por recuperación via email para usuario {}", usuario.getUsername());
    }

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void limpiarTokensExpirados() {
        recoveryTokenRepository.deleteByExpiraEnBefore(Instant.now());
        log.info("Tokens de recuperación 2FA expirados eliminados");
    }

    private String generarCodigo() {
        int code = secureRandom.nextInt((int) Math.pow(10, CODE_LENGTH));
        return String.format("%0" + CODE_LENGTH + "d", code);
    }
}
