CREATE TABLE configuracion_precios (
    id          BIGSERIAL PRIMARY KEY,
    proveedor   VARCHAR(100) NOT NULL UNIQUE,
    margen      DECIMAL(8,4) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO configuracion_precios (proveedor, margen) VALUES
    ('Autopartes del Sur', 22.5),
    ('EGSA', 20.032);
