-- =====================================================================
-- V7 - Proveedor de origen del producto (ej: import de catalogo de proveedor).
-- Opcional: los productos cargados a mano o por IA pueden no tenerlo.
-- =====================================================================

ALTER TABLE productos ADD COLUMN proveedor VARCHAR(150);

CREATE INDEX idx_productos_proveedor ON productos (proveedor);
