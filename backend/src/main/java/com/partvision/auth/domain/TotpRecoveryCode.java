package com.partvision.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Codigo de recuperacion de 2FA (un solo uso). Se guarda HASHEADO (BCrypt): si el usuario
 * pierde el telefono, puede loguear con uno de estos, que queda marcado como usado.
 */
@Entity
@Table(name = "totp_recovery_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private boolean usado;
}
