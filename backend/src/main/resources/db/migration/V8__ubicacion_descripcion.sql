-- Ubicaciones: modelo plano amigable para el cliente.
-- Se agrega una descripcion libre opcional y el tipo pasa a ser opcional
-- (el cliente usa SU nomenclatura de codigo; el tipo/jerarquia deja de ser obligatorio).
ALTER TABLE ubicaciones ADD COLUMN descripcion VARCHAR(300);
ALTER TABLE ubicaciones ALTER COLUMN tipo DROP NOT NULL;
