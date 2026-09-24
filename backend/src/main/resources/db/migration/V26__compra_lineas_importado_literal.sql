-- La planilla del cliente escribe la palabra "Importado" en la columna Codigo cuando la pieza
-- llego sin codigo de catalogo. Hasta el 2026-09-24 solo la celda vacia se guardaba como
-- IMPORTADOS, asi que en el primer envio real 76 de 228 lineas quedaron con ese literal: sin
-- producto, y fuera del listado del boton Importados, que filtra por codigo = 'IMPORTADOS'.
--
-- Hace falta tocar lo ya guardado, ademas de corregir el lector: el codigo entra en la huella
-- de contenido de la factura, asi que con las lineas viejas como estaban el proximo envio de
-- la misma planilla habria dado CONFLICTO en las 62 facturas en vez de SIN_CAMBIOS.
--
-- Solo toca lineas de compra. No hay ningun producto cuyo SKU sea "Importado" (verificado
-- contra la base de produccion), asi que no se pisa ningun codigo real.
UPDATE compra_lineas
   SET codigo = 'IMPORTADOS'
 WHERE upper(btrim(codigo)) IN ('IMPORTADO', 'IMPORTADOS')
   AND codigo <> 'IMPORTADOS';
