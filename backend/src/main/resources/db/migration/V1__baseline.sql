-- =====================================================================
-- V1 - Baseline del esquema PartVision.
-- Solo habilita extensiones necesarias. Las tablas de cada dominio
-- (usuarios, catalogo, ubicaciones, stock, movimientos, ai_extractions)
-- se agregan en migraciones posteriores, una por fase.
-- =====================================================================

-- Busqueda difusa por descripcion / normalizacion de texto (fases futuras).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
