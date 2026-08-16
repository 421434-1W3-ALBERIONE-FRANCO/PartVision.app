package com.partvision.auth.repository;

import com.partvision.auth.domain.TotpRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TotpRecoveryCodeRepository extends JpaRepository<TotpRecoveryCode, Long> {

    List<TotpRecoveryCode> findByUsuarioIdAndUsadoFalse(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
