package com.partvision.auth.service;

import com.partvision.auth.domain.PasswordResetToken;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.repository.PasswordResetTokenRepository;
import com.partvision.auth.repository.UsuarioRepository;
import com.partvision.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${partvision.recovery.token-expiration-minutes:30}")
    private int tokenExpirationMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void solicitarReset(String email) {
        Optional<Usuario> opt = usuarioRepository.findByEmail(email);
        if (opt.isEmpty()) {
            log.debug("Reset solicitado para email no registrado: {}", email);
            return;
        }
        Usuario usuario = opt.get();
        if (!usuario.isActivo()) {
            return;
        }

        tokenRepository.deleteByUsuarioId(usuario.getId());

        String tokenPlano = generarToken();
        String hash = sha256(tokenPlano);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(hash)
                .usuario(usuario)
                .expiraEn(Instant.now().plus(tokenExpirationMinutes, ChronoUnit.MINUTES))
                .build();
        tokenRepository.save(resetToken);

        emailService.enviarResetPassword(email, tokenPlano);
    }

    @Transactional
    public void resetearPassword(String tokenPlano, String nuevaPassword) {
        String hash = sha256(tokenPlano);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Token inválido o expirado"));

        if (resetToken.isUsado() || resetToken.estaExpirado()) {
            throw new BusinessException("Token inválido o expirado");
        }

        Usuario usuario = resetToken.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        resetToken.setUsado(true);
        tokenRepository.save(resetToken);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void limpiarTokensExpirados() {
        tokenRepository.deleteByExpiraEnBefore(Instant.now());
        log.info("Tokens de reset expirados eliminados");
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
