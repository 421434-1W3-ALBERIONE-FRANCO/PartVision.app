package com.partvision.imports.dto;

import java.util.List;

/**
 * Resultado de una importacion masiva: cuantas filas entraron, cuantas se
 * importaron y el detalle de los errores por fila (importacion parcial).
 *
 * @param totalFilas  filas de datos procesadas (sin contar el encabezado)
 * @param importados  filas importadas con exito
 * @param errores     detalle de las filas que fallaron
 */
public record ImportResultResponse(int totalFilas, int importados, List<FilaError> errores) {

    public record FilaError(long fila, String mensaje) {
    }
}
