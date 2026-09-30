-- El codigo IMP-NNNNN de un importado lo asigna el sistema, nunca una persona, y no se repite.
--
-- Hasta ahora el panel proponia el siguiente numero libre y el navegador lo mandaba de vuelta
-- al crear el producto. Eso dejaba tres agujeros:
--   * el campo era editable: el operador podia escribir cualquier codigo;
--   * aunque no lo fuera, el valor viajaba desde el navegador y se podia cambiar en el pedido;
--   * entre "ver el numero libre" y "guardarlo" dos personas podian recibir el mismo.
--
-- La secuencia reparte numeros de a uno aunque dos altas ocurran a la vez, y el indice unico
-- es la garantia final: ningun camino —tampoco la carga masiva, que escribe SQL directo—
-- puede dejar dos productos con el mismo codigo IMP-.
--
-- Un numero que se pide y no se usa (un alta que falla despues) queda salteado. Es lo esperado
-- en una secuencia: importa que no se repitan, no que sean correlativos.
CREATE SEQUENCE seq_sku_importado START WITH 1 MINVALUE 1;

-- Arranca despues del mayor IMP- que exista. Al crear esta migracion no habia ninguno.
SELECT setval('seq_sku_importado',
              COALESCE(MAX(substring(upper(sku) FROM '^IMP-([0-9]+)$')::bigint), 1),
              MAX(substring(upper(sku) FROM '^IMP-([0-9]+)$')) IS NOT NULL)
  FROM productos;

CREATE UNIQUE INDEX uq_productos_sku_importado
    ON productos (upper(sku))
    WHERE upper(sku) LIKE 'IMP-%';
