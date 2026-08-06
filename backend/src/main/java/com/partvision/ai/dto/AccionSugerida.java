package com.partvision.ai.dto;

/**
 * Accion sugerida al analizar una extraccion contra el catalogo actual.
 * NUEVO: no existe -> se puede crear.
 * YA_EXISTE: el producto (por codigo de barras o SKU) ya esta cargado -> evitar duplicado.
 * AGREGAR_CODIGO: el producto existe pero le falta el codigo de barras detectado -> ofrecer agregarlo.
 */
public enum AccionSugerida {
    NUEVO,
    YA_EXISTE,
    AGREGAR_CODIGO
}
