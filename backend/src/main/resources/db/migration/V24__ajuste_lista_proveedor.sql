-- =====================================================================
-- V24 - Separa el recargo del proveedor del margen de reventa.
--
-- Problema: 'margen' se estaba usando para dos cosas distintas. En
-- Autopartes del Sur el Excel exporta el "Precio de Lista", pero el
-- precio que realmente se paga es ese valor + 22.5% (lo que su portal
-- muestra como "Precio Venta"). Al cargar ese 22.5% como margen, el
-- costo quedaba guardado como el precio de lista (mas bajo que el real)
-- y el precio de venta terminaba siendo el costo de compra.
--
-- Solucion: 'ajuste_lista' convierte el precio del archivo en el costo
-- real de compra; 'margen' queda solo para la reventa.
--   costo = precio_archivo * (1 + ajuste_lista/100)
--   venta = costo * (1 + margen/100)
-- EGSA exporta directamente el precio que se paga, asi que su ajuste es 0.
-- =====================================================================

ALTER TABLE configuracion_precios
    ADD COLUMN ajuste_lista DECIMAL(8,4) NOT NULL DEFAULT 0;

UPDATE configuracion_precios SET ajuste_lista = 22.5 WHERE proveedor = 'Autopartes del Sur';
