-- =====================================================================
-- V12 - public_id (UUID) para exponer en rutas externas sin filtrar los IDs
-- secuenciales (proteccion contra IDOR / enumeracion).
--
-- El 'id' BIGINT interno se mantiene (FKs, joins, performance del import masivo);
-- el 'public_id' es el identificador que veran los clientes en URLs y respuestas.
--
-- gen_random_uuid() es built-in desde PostgreSQL 13. Al agregar la columna con ese
-- DEFAULT volatil, Postgres asigna un UUID distinto a CADA fila existente (reescribe
-- la tabla). En 'productos' (~135k) eso tarda unos segundos: es un costo de deploy unico.
-- =====================================================================

ALTER TABLE productos   ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE marcas      ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE categorias  ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE ubicaciones ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE productos   ADD CONSTRAINT uq_productos_public_id   UNIQUE (public_id);
ALTER TABLE marcas      ADD CONSTRAINT uq_marcas_public_id      UNIQUE (public_id);
ALTER TABLE categorias  ADD CONSTRAINT uq_categorias_public_id  UNIQUE (public_id);
ALTER TABLE ubicaciones ADD CONSTRAINT uq_ubicaciones_public_id UNIQUE (public_id);
