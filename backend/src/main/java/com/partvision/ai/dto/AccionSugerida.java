package com.partvision.ai.dto;

/**
 * Accion sugerida al analizar una extraccion contra el catalogo actual.
 * NUEVO: no existe -> se puede crear.
 * YA_EXISTE: el producto (por codigo de barras o SKU) ya esta cargado -> evitar duplicado.
 * AGREGAR_CODIGO: el producto existe pero le falta el codigo de barras detectado -> ofrecer agregarlo.
 * POSIBLES_COINCIDENCIAS: hay productos parecidos (misma base de codigo o descripcion) pero ninguno
 *   matchea con certeza (ej: difieren en la medida o la marca) -> el revisor decide si es uno de
 *   ellos o realmente uno nuevo. La IA NO auto-decide para no generar duplicados ni cruzar variantes.
 */
public enum AccionSugerida {
    NUEVO,
    YA_EXISTE,
    AGREGAR_CODIGO,
    POSIBLES_COINCIDENCIAS
}
