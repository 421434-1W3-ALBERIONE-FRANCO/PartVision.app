-- =====================================================================
-- V4 - Ubicaciones fisicas jerarquicas (adjacency-list + path materializado).
-- Deposito -> Sector -> Pasillo -> Estanteria -> Nivel (flexible/evolutivo).
-- =====================================================================

CREATE TABLE ubicaciones (
    id         BIGINT GENERATED ALWAYS AS IDENTITY,
    parent_id  BIGINT,
    tipo       VARCHAR(20)  NOT NULL,
    codigo     VARCHAR(50)  NOT NULL,
    -- Camino materializado (ej "A/1/4/2") para consultas de subarbol rapidas.
    path       VARCHAR(500) NOT NULL,
    activo     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT pk_ubicaciones PRIMARY KEY (id),
    CONSTRAINT fk_ubicaciones_parent FOREIGN KEY (parent_id) REFERENCES ubicaciones (id),
    -- Codigo unico entre hermanos (NULLS NOT DISTINCT cubre las raices).
    CONSTRAINT uq_ubicaciones_parent_codigo UNIQUE NULLS NOT DISTINCT (parent_id, codigo)
);

CREATE INDEX idx_ubicaciones_parent ON ubicaciones (parent_id);
CREATE INDEX idx_ubicaciones_path   ON ubicaciones (path);
