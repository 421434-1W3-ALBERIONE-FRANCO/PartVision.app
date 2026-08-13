-- =====================================================================
-- V10 - Columna de busqueda denormalizada para busqueda rapida y tolerante a typos.
--
-- Problema: la busqueda abarca descripcion + sku + marca + categoria, pero marca y
-- categoria viven en otras tablas y solo descripcion tenia indice trigram. Buscar con
-- similaridad (typos) sobre varios campos hacia seq scans lentos (~800ms sobre 135k).
--
-- Solucion: una columna 'busqueda' (lower de descripcion+sku+marca+categoria) con su
-- propio indice GIN trigram. La mantiene un trigger (no hay que tocar el codigo de alta,
-- edicion ni import). Con esto, la busqueda por palabras con '<%' usa el indice: ~40ms.
-- =====================================================================

ALTER TABLE productos ADD COLUMN busqueda text;

-- Calcula 'busqueda' a partir de los campos del producto + nombres de marca/categoria.
CREATE OR REPLACE FUNCTION productos_set_busqueda() RETURNS trigger AS $$
BEGIN
    NEW.busqueda := lower(
        coalesce(NEW.descripcion, '') || ' ' ||
        coalesce(NEW.sku, '') || ' ' ||
        coalesce((SELECT nombre FROM marcas WHERE id = NEW.marca_id), '') || ' ' ||
        coalesce((SELECT nombre FROM categorias WHERE id = NEW.categoria_id), ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_productos_busqueda
    BEFORE INSERT OR UPDATE ON productos
    FOR EACH ROW EXECUTE FUNCTION productos_set_busqueda();

-- Si se renombra una marca o categoria, refresca la 'busqueda' de sus productos
-- (el UPDATE dispara el trigger de productos, que recalcula el texto).
CREATE OR REPLACE FUNCTION refrescar_busqueda_por_marca() RETURNS trigger AS $$
BEGIN
    IF NEW.nombre IS DISTINCT FROM OLD.nombre THEN
        UPDATE productos SET busqueda = busqueda WHERE marca_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_marcas_refresca_busqueda
    AFTER UPDATE ON marcas FOR EACH ROW EXECUTE FUNCTION refrescar_busqueda_por_marca();

CREATE OR REPLACE FUNCTION refrescar_busqueda_por_categoria() RETURNS trigger AS $$
BEGIN
    IF NEW.nombre IS DISTINCT FROM OLD.nombre THEN
        UPDATE productos SET busqueda = busqueda WHERE categoria_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_categorias_refresca_busqueda
    AFTER UPDATE ON categorias FOR EACH ROW EXECUTE FUNCTION refrescar_busqueda_por_categoria();

-- Poblar las filas ya existentes (el UPDATE dispara el trigger que calcula 'busqueda').
UPDATE productos SET busqueda = NULL;

-- Indice para acelerar los operadores trigram (similaridad e ILIKE) sobre 'busqueda'.
CREATE INDEX idx_productos_busqueda_trgm ON productos USING gin (busqueda gin_trgm_ops);
