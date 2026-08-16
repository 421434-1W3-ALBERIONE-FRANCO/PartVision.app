-- =====================================================================
-- V13 - 2FA por TOTP (Google Authenticator / Authy / etc.).
--
-- Segundo factor OPCIONAL por usuario (el codigo puede exigirlo obligatorio para ADMIN).
-- Flujo: el usuario escanea un QR (otpauth://) una vez, su app genera un codigo de 6
-- digitos cada 30s, y en el login lo ingresa despues del password.
--
-- totp_secret: semilla del TOTP. Es reversible por naturaleza (se necesita para verificar),
--   asi que NO se hashea. (Hardening futuro: cifrarla at-rest con una clave de entorno.)
-- totp_enabled: si el 2FA esta activo (recien tras confirmar el primer codigo).
-- =====================================================================

ALTER TABLE usuarios ADD COLUMN totp_secret  TEXT;
ALTER TABLE usuarios ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT false;

-- Codigos de recuperacion de un solo uso (si el usuario pierde el telefono).
-- Se guardan HASHEADOS (BCrypt): nunca en claro; al usarse se marcan como usados.
CREATE TABLE totp_recovery_codes (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    code_hash  VARCHAR(100) NOT NULL,
    usado      BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_totp_recovery_usuario ON totp_recovery_codes (usuario_id);
