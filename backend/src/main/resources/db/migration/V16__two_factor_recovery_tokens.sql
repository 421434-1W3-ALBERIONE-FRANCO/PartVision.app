CREATE TABLE two_factor_recovery_tokens (
    id          BIGSERIAL PRIMARY KEY,
    code_hash   VARCHAR(64)  NOT NULL,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_2frt_usuario ON two_factor_recovery_tokens (usuario_id);
CREATE INDEX idx_2frt_expira  ON two_factor_recovery_tokens (expira_en);
