-- =====================================================================
-- V5 - Stock por (producto, ubicacion) + ledger de movimientos.
-- El stock es el saldo cacheado; movimientos_stock es el historial append-only.
-- =====================================================================

CREATE TABLE stock (
    id           BIGINT  GENERATED ALWAYS AS IDENTITY,
    producto_id  BIGINT  NOT NULL,
    ubicacion_id BIGINT  NOT NULL,
    cantidad     INTEGER NOT NULL DEFAULT 0,
    version      BIGINT  NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    created_by   BIGINT,
    updated_by   BIGINT,
    CONSTRAINT pk_stock PRIMARY KEY (id),
    CONSTRAINT fk_stock_producto  FOREIGN KEY (producto_id)  REFERENCES productos (id),
    CONSTRAINT fk_stock_ubicacion FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones (id),
    CONSTRAINT uq_stock_producto_ubicacion UNIQUE (producto_id, ubicacion_id),
    CONSTRAINT ck_stock_no_negativo CHECK (cantidad >= 0)
);

CREATE INDEX idx_stock_producto  ON stock (producto_id);
CREATE INDEX idx_stock_ubicacion ON stock (ubicacion_id);

CREATE TABLE movimientos_stock (
    id                   BIGINT  GENERATED ALWAYS AS IDENTITY,
    producto_id          BIGINT  NOT NULL,
    tipo                 VARCHAR(20) NOT NULL,
    cantidad             INTEGER NOT NULL,
    ubicacion_origen_id  BIGINT,
    ubicacion_destino_id BIGINT,
    motivo               VARCHAR(300),
    referencia           VARCHAR(100),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    created_by           BIGINT,
    updated_by           BIGINT,
    CONSTRAINT pk_movimientos_stock PRIMARY KEY (id),
    CONSTRAINT fk_mov_producto FOREIGN KEY (producto_id)          REFERENCES productos (id),
    CONSTRAINT fk_mov_origen   FOREIGN KEY (ubicacion_origen_id)  REFERENCES ubicaciones (id),
    CONSTRAINT fk_mov_destino  FOREIGN KEY (ubicacion_destino_id) REFERENCES ubicaciones (id),
    CONSTRAINT ck_mov_cantidad_positiva CHECK (cantidad > 0)
);

CREATE INDEX idx_mov_producto_fecha ON movimientos_stock (producto_id, created_at);
CREATE INDEX idx_mov_created_at      ON movimientos_stock (created_at);
CREATE INDEX idx_mov_created_by      ON movimientos_stock (created_by);
