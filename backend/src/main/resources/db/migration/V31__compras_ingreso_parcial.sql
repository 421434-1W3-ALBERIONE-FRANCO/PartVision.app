-- Una compra se ingresa por partes: esta INGRESADA recien cuando no le falta ubicar ninguna
-- linea con articulo. Hasta ahora bastaba ubicar una sola para marcarla entera, y las demas
-- quedaban fuera del stock sin forma de ingresarlas despues.
--
-- Al crear esta migracion habia dos compras asi en produccion:
--   0018-00006900  1 de 5 lineas ubicadas, 12 de 25 unidades en el stock
--   900004165      3 de 5 lineas ubicadas, 13 de 18 unidades en el stock
--
-- Vuelven a POR_UBICAR sin tocar el stock: lo que ya entro queda donde esta, y las lineas que
-- faltan aparecen con su selector de ubicacion para terminar de ingresarlas.
UPDATE compras c
   SET estado = 'POR_UBICAR'
 WHERE c.estado = 'INGRESADA'
   AND EXISTS (SELECT 1
                 FROM compra_lineas l
                WHERE l.compra_id = c.id
                  AND l.producto_id IS NOT NULL
                  AND l.revision IS DISTINCT FROM 'DESCARTADA'
                  AND l.ubicacion_ingreso_id IS NULL);
