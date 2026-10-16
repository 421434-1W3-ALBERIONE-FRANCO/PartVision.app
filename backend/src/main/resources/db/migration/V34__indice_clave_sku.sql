-- Para encontrar un producto aunque el proveedor mande su codigo escrito de otra forma: el
-- 2026-10-15 EGSA paso a mandar 651 codigos de PACKSON con un guion de mas (PK-2000 por
-- PK2000). La expresion tiene que ser la misma que usa ProductoRepository.findByClaveSkuIn y
-- CodigoProducto.clave en Java: mayusculas, sin espacios, puntos, guiones ni barras.
CREATE INDEX IF NOT EXISTS ix_productos_clave_sku
    ON productos (upper(regexp_replace(sku, '[\s./-]', '', 'g')));
