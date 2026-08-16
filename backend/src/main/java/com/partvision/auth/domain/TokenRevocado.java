package com.partvision.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Token invalidado antes de su expiracion natural (logout). Se identifica por el 'jti'
 * (claim id unico del JWT). El filtro consulta esta tabla para rechazarlo.
 */
@Entity
@Table(name = "tokens_revocados")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenRevocado {

    @Id
    private String jti;

    @Column(name = "revocado_en", nullable = false)
    private Instant revocadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;
}
