CREATE TABLE import_precio_batch (
    id          BIGSERIAL PRIMARY KEY,
    proveedor   VARCHAR(100) NOT NULL,
    fuente      VARCHAR(20) NOT NULL,
    archivo     VARCHAR(255),
    total       INT NOT NULL DEFAULT 0,
    aplicados   INT NOT NULL DEFAULT 0,
    omitidos    INT NOT NULL DEFAULT 0,
    conflictos  INT NOT NULL DEFAULT 0,
    estado      VARCHAR(20) NOT NULL DEFAULT 'APLICADO',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE historial_precios (
    id                      BIGSERIAL PRIMARY KEY,
    producto_id             BIGINT NOT NULL REFERENCES productos(id),
    batch_id                BIGINT NOT NULL REFERENCES import_precio_batch(id),
    precio_costo_anterior   DECIMAL(12,2),
    precio_venta_anterior   DECIMAL(12,2),
    precio_costo_nuevo      DECIMAL(12,2),
    precio_venta_nuevo      DECIMAL(12,2),
    margen_aplicado         DECIMAL(8,4),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_historial_precios_producto ON historial_precios(producto_id);
CREATE INDEX idx_historial_precios_batch ON historial_precios(batch_id);
