CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    expira_en   TIMESTAMPTZ NOT NULL,
    usado       BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prt_usuario ON password_reset_tokens (usuario_id);
CREATE INDEX idx_prt_expira  ON password_reset_tokens (expira_en);
