package com.partvision.inventory.dto;

/**
 * Resultado de un conteo: la cantidad que había, la que quedó y el movimiento de
 * ajuste generado (null si no hubo diferencia).
 */
public record ConteoResponse(
        Long productoId,
        Long ubicacionId,
        int cantidadAnterior,
        int cantidadNueva,
        MovimientoResponse movimiento
) {
}
