-- Las facturas de compra llegan desde la planilla del cliente (via Power Automate) y es la
-- planilla la que decide el estado: EN TRANSITO mientras la mercaderia no llego, INGRESADA
-- cuando llego. Pero que haya llegado no significa que este en el stock: el stock se carga
-- cuando en el panel se le asigna una ubicacion a cada linea. POR_UBICAR es ese estado
-- intermedio, y INGRESADA pasa a significar "stock cargado en PartVision".
ALTER TABLE compras DROP CONSTRAINT chk_compras_estado;
ALTER TABLE compras ADD CONSTRAINT chk_compras_estado
    CHECK (estado IN ('EN_TRANSITO', 'POR_UBICAR', 'INGRESADA'));
