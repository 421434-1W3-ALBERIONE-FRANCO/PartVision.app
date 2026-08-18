ALTER TABLE usuarios ADD COLUMN email VARCHAR(255);

CREATE UNIQUE INDEX uq_usuarios_email ON usuarios (email) WHERE email IS NOT NULL;
