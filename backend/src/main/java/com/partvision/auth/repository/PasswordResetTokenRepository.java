package com.partvision.auth.repository;

import com.partvision.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByExpiraEnBefore(Instant instant);

    void deleteByUsuarioId(Long usuarioId);
}
