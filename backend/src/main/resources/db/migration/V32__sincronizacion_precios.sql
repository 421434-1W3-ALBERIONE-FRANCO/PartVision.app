-- Actualizacion automatica de precios desde el portal del proveedor (por ahora, Autopartes del
-- Sur). Cada corrida deja una fila: es la constancia de que se actualizo, de que no hubo cambios,
-- o de por que no se aplico. Los precios que cambian demasiado de golpe no se aplican solos:
-- quedan en precio_revisiones hasta que una persona los apruebe o los descarte.
CREATE TABLE sincronizaciones_precios (
    id                BIGSERIAL PRIMARY KEY,
    proveedor         VARCHAR(100) NOT NULL,
    -- AUTOMATICA (la programada) o MANUAL (el boton "Actualizar ahora")
    origen            VARCHAR(20)  NOT NULL,
    forzada           BOOLEAN      NOT NULL DEFAULT FALSE,
    iniciada_en       TIMESTAMP    NOT NULL,
    terminada_en      TIMESTAMP,
    -- EN_CURSO, ACTUALIZADA, SIN_CAMBIOS, RETENIDA, ERROR
    resultado         VARCHAR(20)  NOT NULL,
    mensaje           TEXT,
    -- Un problema por linea, para mostrarle al cliente
    problemas         TEXT,
    batch_id          BIGINT REFERENCES import_precio_batch (id),
    filas_lista       INT NOT NULL DEFAULT 0,
    actualizados      INT NOT NULL DEFAULT 0,
    sin_cambio        INT NOT NULL DEFAULT 0,
    no_encontrados    INT NOT NULL DEFAULT 0,
    filas_invalidas   INT NOT NULL DEFAULT 0,
    en_revision       INT NOT NULL DEFAULT 0,
    -- Productos cuyo precio en la cuenta del cliente difiere del de la lista publica
    -- (sus descuentos). NULL si no se pudo comparar.
    con_precio_propio INT
);

CREATE INDEX ix_sincronizaciones_precios_proveedor ON sincronizaciones_precios (proveedor, iniciada_en DESC);

CREATE TABLE precio_revisiones (
    id                 BIGSERIAL PRIMARY KEY,
    sincronizacion_id  BIGINT        NOT NULL REFERENCES sincronizaciones_precios (id),
    producto_id        BIGINT        NOT NULL REFERENCES productos (id),
    -- Precio tal cual viene en la lista: el costo y la venta se recalculan al aprobar, con la
    -- configuracion de ese momento.
    precio_lista       NUMERIC(12, 2) NOT NULL,
    costo_actual       NUMERIC(12, 2),
    costo_nuevo        NUMERIC(12, 2) NOT NULL,
    variacion_pct      NUMERIC(10, 2),
    -- PENDIENTE, APLICADA, DESCARTADA, o VENCIDA (la reemplazo una corrida mas nueva)
    estado             VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    resuelta_en        TIMESTAMP
);

CREATE INDEX ix_precio_revisiones_pendientes ON precio_revisiones (estado) WHERE estado = 'PENDIENTE';
