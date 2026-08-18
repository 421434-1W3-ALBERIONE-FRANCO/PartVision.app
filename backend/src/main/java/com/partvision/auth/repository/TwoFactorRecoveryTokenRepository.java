package com.partvision.auth.repository;

import com.partvision.auth.domain.TwoFactorRecoveryToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TwoFactorRecoveryTokenRepository extends JpaRepository<TwoFactorRecoveryToken, Long> {

    Optional<TwoFactorRecoveryToken> findByUsuarioIdAndUsadoFalse(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);

    void deleteByExpiraEnBefore(Instant instant);
}
