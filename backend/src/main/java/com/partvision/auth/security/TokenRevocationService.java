package com.partvision.auth.security;

import com.partvision.auth.domain.TokenRevocado;
import com.partvision.auth.repository.TokenRevocadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Lista negra de tokens invalidados (logout real). El filtro consulta {@link #estaRevocado}
 * en cada request; el logout llama a {@link #revocar}. Un job diario purga los ya vencidos.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final TokenRevocadoRepository repository;

    /** Marca un token como revocado (idempotente). {@code expira} = expiracion natural del token. */
    @Transactional
    public void revocar(String jti, Instant expira) {
        if (jti == null || jti.isBlank() || repository.existsById(jti)) {
            return;
        }
        repository.save(new TokenRevocado(jti, Instant.now(), expira));
    }

    @Transactional(readOnly = true)
    public boolean estaRevocado(String jti) {
        return jti != null && !jti.isBlank() && repository.existsById(jti);
    }

    /** Borra las filas cuya expiracion natural ya paso: la lista negra no crece indefinidamente. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limpiarExpirados() {
        repository.deleteByExpiraEnBefore(Instant.now());
    }
}
