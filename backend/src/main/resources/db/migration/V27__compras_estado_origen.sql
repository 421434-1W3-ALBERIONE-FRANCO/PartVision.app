-- Quien dejo la compra en el estado que tiene: la planilla del cliente o el panel.
--
-- Sin esto no hay forma de distinguir dos situaciones opuestas cuando la planilla dice
-- EN TRANSITO y en PartVision la compra ya esta INGRESADA:
--   * la ingresamos a mano adelantandonos a la planilla -> es a proposito, no hay que avisar;
--   * la planilla dijo INGRESADA, se cargo el stock, y despues alguien la edito hacia atras
--     -> eso si hay que avisarlo, porque el stock ya esta cargado.
ALTER TABLE compras ADD COLUMN estado_origen VARCHAR(10) NOT NULL DEFAULT 'PLANILLA';

ALTER TABLE compras ADD CONSTRAINT chk_compras_estado_origen
    CHECK (estado_origen IN ('PLANILLA', 'PANEL'));

-- Las que ya estan INGRESADA solo pudieron llegar ahi por el panel: cargar el stock asignando
-- ubicacion a cada linea es el unico camino que existe hacia ese estado.
UPDATE compras SET estado_origen = 'PANEL' WHERE estado = 'INGRESADA';
