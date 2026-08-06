package com.partvision.imports.dto;

import java.util.List;

/**
 * Resultado de una importacion masiva: cuantas filas entraron, cuantas se
 * importaron, cuantas se omitieron por duplicado y el detalle de los errores
 * por fila (importacion parcial).
 *
 * @param totalFilas  filas de datos procesadas (sin contar el encabezado)
 * @param importados  filas importadas con exito
 * @param omitidos    filas salteadas por ser duplicado (en el archivo o ya en la BD)
 * @param errores     detalle de las filas que fallaron por otro motivo
 */
public record ImportResultResponse(int totalFilas, int importados, int omitidos, List<FilaError> errores) {

    public record FilaError(long fila, String mensaje) {
    }
}
