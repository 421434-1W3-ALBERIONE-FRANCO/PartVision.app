-- Los productos con la descripcion mas parecida a una linea de compra que llego sin codigo
-- (ProductoRepositoryImpl.parecidosPorDescripcion). El indice GIN que ya existe sirve para
-- "todos los que superan un umbral", y con palabras comunes ("junta tapa cilindro") eso son
-- miles de candidatos: en el server tardaba de 50 ms a 12 s. GiST permite pedir directamente
-- los N mas cercanos (ORDER BY descripcion <-> texto LIMIT N): ~150 ms siempre. Pesa ~34 MB.
CREATE INDEX IF NOT EXISTS ix_productos_descripcion_gist
    ON productos USING gist (descripcion gist_trgm_ops);
