-- =====================================================================
-- V6 - Extracciones IA. Guarda la imagen (referencia), lo que la IA sugirio,
-- el modelo usado y el resultado de la revision humana. La IA NUNCA escribe
-- en el catalogo: el producto se crea recien al confirmar (estado CONFIRMADA).
-- =====================================================================

CREATE TABLE ai_extractions (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY,
    imagen_key             VARCHAR(500) NOT NULL,
    modelo                 VARCHAR(100) NOT NULL,
    prompt_version         VARCHAR(50),
    datos_sugeridos        JSONB        NOT NULL DEFAULT '{}',
    estado                 VARCHAR(20)  NOT NULL,
    producto_id            BIGINT,
    usuario_confirmador_id BIGINT,
    confirmado_en          TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             BIGINT,
    updated_by             BIGINT,
    CONSTRAINT pk_ai_extractions PRIMARY KEY (id),
    CONSTRAINT fk_ai_extractions_producto FOREIGN KEY (producto_id) REFERENCES productos (id)
);

CREATE INDEX idx_ai_extractions_estado ON ai_extractions (estado);
