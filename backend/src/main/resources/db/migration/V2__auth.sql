-- =====================================================================
-- V2 - Autenticacion: usuarios, roles y su relacion N:M.
-- =====================================================================

CREATE TABLE roles (
    id     BIGINT GENERATED ALWAYS AS IDENTITY,
    nombre VARCHAR(50) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_nombre UNIQUE (nombre)
);

CREATE TABLE usuarios (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nombre        VARCHAR(150) NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_username UNIQUE (username)
);

CREATE TABLE usuarios_roles (
    usuario_id BIGINT NOT NULL,
    rol_id     BIGINT NOT NULL,
    CONSTRAINT pk_usuarios_roles PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT fk_ur_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_rol     FOREIGN KEY (rol_id)     REFERENCES roles (id)
);

CREATE INDEX idx_usuarios_roles_usuario ON usuarios_roles (usuario_id);

-- Roles iniciales. Futuros (ATENCION_PUBLICO, DEPOSITO, SUPERVISOR) se agregan
-- por migracion cuando se necesiten.
INSERT INTO roles (nombre) VALUES ('ADMIN'), ('OPERARIO'), ('ADMINISTRACION');

-- Usuario administrador de arranque (bootstrap).
-- Password de desarrollo: 'admin1234' (hash BCrypt). CAMBIAR en produccion.
INSERT INTO usuarios (username, password_hash, nombre, activo, created_at, updated_at)
VALUES ('admin', '$2a$10$opPIBDgeJiqUBSsV6.cE.OIhc4vurhSy2zDcE/ZAemA/k44jwbqEC',
        'Administrador', TRUE, NOW(), NOW());

INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT u.id, r.id
FROM usuarios u, roles r
WHERE u.username = 'admin' AND r.nombre = 'ADMIN';
