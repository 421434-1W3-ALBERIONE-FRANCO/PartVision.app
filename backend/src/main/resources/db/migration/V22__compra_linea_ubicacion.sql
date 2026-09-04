ALTER TABLE compra_lineas
    ADD COLUMN ubicacion_ingreso_id BIGINT REFERENCES ubicaciones(id);
