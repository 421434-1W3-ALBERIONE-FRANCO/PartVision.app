package com.partvision.ai.dto;

/**
 * Resultado de analizar una extraccion contra el catalogo: que conviene hacer con ella.
 *
 * @param accion                    NUEVO / YA_EXISTE / AGREGAR_CODIGO
 * @param productoExistenteId       producto que matcheo (null si NUEVO)
 * @param productoExistenteDescripcion  descripcion del match, para mostrar (null si NUEVO)
 * @param codigoBarras              codigo de barras detectado (para AGREGAR_CODIGO; puede ser null)
 * @param mensaje                   leyenda lista para mostrar al revisor
 */
public record SugerenciaAccionResponse(
        AccionSugerida accion,
        Long productoExistenteId,
        String productoExistenteDescripcion,
        String codigoBarras,
        String mensaje
) {
}
