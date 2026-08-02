-- =====================================================================
-- V3 - Catalogo: marcas, categorias, productos y sus codigos.
-- =====================================================================

CREATE TABLE marcas (
    id         BIGINT GENERATED ALWAYS AS IDENTITY,
    nombre     VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT pk_marcas PRIMARY KEY (id),
    CONSTRAINT uq_marcas_nombre UNIQUE (nombre)
);

CREATE TABLE categorias (
    id         BIGINT GENERATED ALWAYS AS IDENTITY,
    nombre     VARCHAR(150) NOT NULL,
    parent_id  BIGINT,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT pk_categorias PRIMARY KEY (id),
    CONSTRAINT fk_categorias_parent FOREIGN KEY (parent_id) REFERENCES categorias (id),
    -- NULLS NOT DISTINCT: evita categorias raiz duplicadas con el mismo nombre (Postgres 15+).
    CONSTRAINT uq_categorias_nombre_parent UNIQUE NULLS NOT DISTINCT (nombre, parent_id)
);

CREATE INDEX idx_categorias_parent ON categorias (parent_id);

CREATE TABLE productos (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    sku            VARCHAR(100),
    marca_id       BIGINT,
    categoria_id   BIGINT,
    descripcion    VARCHAR(500) NOT NULL,
    estado         VARCHAR(20)  NOT NULL,
    detalles_extra JSONB        NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     BIGINT,
    updated_by     BIGINT,
    CONSTRAINT pk_productos PRIMARY KEY (id),
    CONSTRAINT fk_productos_marca     FOREIGN KEY (marca_id)     REFERENCES marcas (id),
    CONSTRAINT fk_productos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id)
);

-- D2: SKU unico por marca (solo cuando el SKU esta presente).
CREATE UNIQUE INDEX uq_productos_marca_sku ON productos (marca_id, sku) WHERE sku IS NOT NULL;
CREATE INDEX idx_productos_marca     ON productos (marca_id);
CREATE INDEX idx_productos_categoria ON productos (categoria_id);
CREATE INDEX idx_productos_estado    ON productos (estado);
-- Busqueda difusa por descripcion (fase futura de busqueda).
CREATE INDEX idx_productos_descripcion_trgm ON productos USING gin (descripcion gin_trgm_ops);

-- D1: un producto puede tener multiples codigos (barras/interno/proveedor).
CREATE TABLE producto_codigos (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    producto_id BIGINT       NOT NULL,
    codigo      VARCHAR(100) NOT NULL,
    tipo        VARCHAR(30),
    CONSTRAINT pk_producto_codigos PRIMARY KEY (id),
    CONSTRAINT fk_producto_codigos_producto FOREIGN KEY (producto_id) REFERENCES productos (id) ON DELETE CASCADE,
    CONSTRAINT uq_producto_codigos_codigo UNIQUE (codigo)
);

CREATE INDEX idx_producto_codigos_producto ON producto_codigos (producto_id);
