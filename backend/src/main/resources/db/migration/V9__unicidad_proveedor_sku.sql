-- =====================================================================
-- V9 - Unicidad de catalogo por (proveedor, sku).
--
-- Los catalogos de proveedor sin marca (ej. Autopartes del Sur) no quedaban
-- cubiertos por el indice unico (marca_id, sku) (marca_id NULL => no aplica),
-- por lo que reimportar duplicaba productos. Este indice enforce a nivel base
-- la regla "mismo proveedor + mismo codigo = mismo producto".
--
-- Migracion auto-sanadora: primero elimina duplicados que pudieran existir
-- (conserva el id mas chico por proveedor+sku), luego crea el indice unico.
-- Es no-op si el catalogo ya esta limpio.
-- =====================================================================

-- 1. Ids de productos duplicados por (lower(proveedor), lower(sku)), conservando el mas viejo.
CREATE TEMP TABLE productos_dup AS
SELECT id FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY lower(proveedor), lower(sku) ORDER BY id) AS rn
    FROM productos
    WHERE proveedor IS NOT NULL AND sku IS NOT NULL
) t
WHERE t.rn > 1;

-- 2. Borrar las dependencias de esos duplicados (respetando las FKs).
DELETE FROM movimientos_stock WHERE producto_id IN (SELECT id FROM productos_dup);
DELETE FROM stock            WHERE producto_id IN (SELECT id FROM productos_dup);
DELETE FROM producto_codigos WHERE producto_id IN (SELECT id FROM productos_dup);
UPDATE ai_extractions SET producto_id = NULL WHERE producto_id IN (SELECT id FROM productos_dup);

-- 3. Borrar los productos duplicados.
DELETE FROM productos WHERE id IN (SELECT id FROM productos_dup);

DROP TABLE productos_dup;

-- 4. Indice unico parcial: solo cuando proveedor y sku estan presentes.
CREATE UNIQUE INDEX uq_productos_proveedor_sku
    ON productos (lower(proveedor), lower(sku))
    WHERE proveedor IS NOT NULL AND sku IS NOT NULL;
