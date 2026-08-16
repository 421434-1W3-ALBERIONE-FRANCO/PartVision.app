-- =====================================================================
-- V12 - Indice Full-Text Search sobre la columna denormalizada 'busqueda'.
--
-- El buscador pasa a usar FTS (indice invertido) como recuperacion PRIMARIA: encuentra
-- por interseccion de listas de palabras en milisegundos, incluso para terminos comunes
-- (cojinete = 27k docs -> ~5ms), vs los cientos de ms del trigram, que se degradaba a >1s
-- porque tenia que re-verificar decenas de miles de filas.
--
-- El indice trigram existente (idx_productos_busqueda_trgm) SE CONSERVA: se usa como
-- FALLBACK cuando FTS no encuentra nada (typos: "pistpn" -> PISTON, o singular/plural).
--
-- Config 'simple' (sin stemming) a proposito: preserva codigos y medidas (1.6, 8v, ea111,
-- 4d56) y nombres de modelo, que el stemmer 'spanish' romperia (ej. "suran" -> "sur").
-- =====================================================================

CREATE INDEX idx_productos_busqueda_fts ON productos USING gin (to_tsvector('simple', busqueda));
