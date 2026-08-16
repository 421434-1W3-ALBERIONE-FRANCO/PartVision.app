-- =====================================================================
-- V11 - Revocacion de sesion (logout real).
--
-- Hasta ahora el logout solo borraba la cookie del navegador, pero el token seguia
-- siendo valido hasta su expiracion (12h). Esta tabla guarda el 'jti' (id unico) de
-- cada token invalidado en el logout; el filtro JWT rechaza cualquier token cuyo jti
-- este aca, aunque la firma sea valida.
--
-- Se conserva cada fila solo hasta que el token expira naturalmente (expira_en); un
-- job programado limpia las vencidas para que la tabla no crezca sin fin.
-- =====================================================================

CREATE TABLE tokens_revocados (
    jti         VARCHAR(64) PRIMARY KEY,
    revocado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en   TIMESTAMPTZ NOT NULL
);

-- Acelera el borrado periodico de los vencidos.
CREATE INDEX idx_tokens_revocados_expira ON tokens_revocados (expira_en);
