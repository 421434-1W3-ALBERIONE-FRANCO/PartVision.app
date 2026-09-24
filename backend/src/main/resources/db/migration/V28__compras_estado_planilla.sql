-- Reemplaza estado_origen (V27) por estado_planilla. El campo anterior guardaba QUIEN dejo la
-- compra en su estado, y eso mezcla dos preguntas distintas que hay que poder responder por
-- separado. Con el anterior quedaban dos agujeros:
--
--   * INGRESADA solo se alcanza cargando stock desde el panel, asi que su origen siempre era
--     PANEL: la rama que avisaba "la planilla volvio atras con el stock ya cargado" nunca se
--     podia alcanzar;
--   * un cambio hecho a mano se deshacia solo, porque el siguiente envio de la planilla veia
--     un estado distinto al suyo y lo pisaba.
--
-- Guardar lo ultimo que dijo la planilla responde las dos: si nuestro estado difiere del suyo
-- es porque lo movio el panel, y si el valor que manda ahora es distinto del que mando la vez
-- anterior es porque la planilla cambio de opinion. La regla que sale de ahi es una sola:
-- la planilla pisa el estado solo cuando la planilla CAMBIA.
ALTER TABLE compras DROP CONSTRAINT IF EXISTS chk_compras_estado_origen;
ALTER TABLE compras DROP COLUMN IF EXISTS estado_origen;

ALTER TABLE compras ADD COLUMN estado_planilla VARCHAR(20) NOT NULL DEFAULT 'EN_TRANSITO';

ALTER TABLE compras ADD CONSTRAINT chk_compras_estado_planilla
    CHECK (estado_planilla IN ('EN_TRANSITO', 'POR_UBICAR'));

-- INGRESADA es un estado nuestro, no de la planilla: para esas, lo que la planilla decia era
-- INGRESADA, que de este lado se llama POR_UBICAR. Para el resto, lo que decia es su estado.
UPDATE compras
   SET estado_planilla = CASE WHEN estado = 'INGRESADA' THEN 'POR_UBICAR' ELSE estado END;
