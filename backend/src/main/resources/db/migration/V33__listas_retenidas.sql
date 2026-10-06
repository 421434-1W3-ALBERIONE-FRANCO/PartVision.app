-- La lista de precios que mando el robot de un proveedor (EGSA) y quedo retenida porque llego rara
-- (cortada, columnas corridas, muchos saltos de golpe). EGSA no tiene portal para volver a bajarla,
-- asi que se guarda el archivo para poder aplicarla despues de que una persona la reviso
-- ("Aplicar igual"). Una sola por proveedor: la nueva reemplaza a la anterior.
CREATE TABLE listas_retenidas (
    proveedor   VARCHAR(100) PRIMARY KEY,
    archivo     BYTEA        NOT NULL,
    nombre      VARCHAR(255),
    recibida_en TIMESTAMP    NOT NULL DEFAULT NOW()
);
