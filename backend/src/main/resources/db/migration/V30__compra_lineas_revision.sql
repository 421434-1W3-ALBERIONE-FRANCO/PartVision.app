-- Lineas con una cantidad fuera de lo normal: entran, pero quedan esperando que una persona
-- decida si la acepta o la descarta. Reemplaza al rechazo automatico de mas de 10.000
-- unidades: con un tope de 300 hay compras reales que pueden superarlo, y la decision tiene
-- que ser de alguien que conoce el negocio, no del sistema.
--
--   NULL        la cantidad es normal: no hay nada que revisar.
--   PENDIENTE   supera el tope y nadie la miro todavia. La compra no se puede ingresar.
--   ACEPTADA    la revisaron y la cantidad es real: entra al stock como cualquier otra.
--   DESCARTADA  la revisaron y es un error de la planilla: no entra al stock ni a los totales.
--
-- Descartar MARCA la linea en vez de borrarla, a proposito. La planilla sigue teniendo esa
-- fila: si la linea desapareciera de PartVision, la factura guardada dejaria de coincidir con
-- la planilla y cada corrida del flujo la reportaria como conflicto.
ALTER TABLE compra_lineas ADD COLUMN revision VARCHAR(12);

ALTER TABLE compra_lineas ADD CONSTRAINT chk_compra_lineas_revision
    CHECK (revision IS NULL OR revision IN ('PENDIENTE', 'ACEPTADA', 'DESCARTADA'));

-- Las que ya estan guardadas y superan el tope. Solo de compras sin ingresar: una ingresada
-- ya cargo su stock, y marcarla ahora no cambiaria nada. Al crear esta migracion era una sola
-- linea (factura 900004152, 1.197.421 unidades); lo mas alto legitimo eran 240.
UPDATE compra_lineas l
   SET revision = 'PENDIENTE'
  FROM compras c
 WHERE c.id = l.compra_id
   AND c.estado <> 'INGRESADA'
   AND l.cantidad > 300;
