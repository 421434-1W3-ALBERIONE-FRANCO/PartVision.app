package com.partvision.auth.repository;

import com.partvision.auth.domain.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    /** Borra los tokens cuya expiracion natural ya paso. Devuelve cuantos borro. */
    long deleteByExpiraEnBefore(Instant momento);
}
