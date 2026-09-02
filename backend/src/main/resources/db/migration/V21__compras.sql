-- Módulo de compras: recepción de facturas desde Power Automate

CREATE TABLE compras (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_factura  VARCHAR(50)  NOT NULL,
    fecha_factura   DATE         NOT NULL,
    proveedor       VARCHAR(100),
    estado          VARCHAR(20)  NOT NULL DEFAULT 'EN_TRANSITO',
    ubicacion_ingreso_id BIGINT REFERENCES ubicaciones(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uq_compras_numero_factura UNIQUE (numero_factura),
    CONSTRAINT chk_compras_estado CHECK (estado IN ('EN_TRANSITO', 'INGRESADA'))
);

CREATE TABLE compra_lineas (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id   BIGINT       NOT NULL REFERENCES compras(id) ON DELETE CASCADE,
    codigo      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    cantidad    INT          NOT NULL,
    producto_id BIGINT       REFERENCES productos(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    CONSTRAINT chk_compra_linea_cantidad CHECK (cantidad > 0)
);

CREATE INDEX idx_compras_estado ON compras(estado);
CREATE INDEX idx_compras_fecha ON compras(fecha_factura DESC);
CREATE INDEX idx_compra_lineas_compra ON compra_lineas(compra_id);
