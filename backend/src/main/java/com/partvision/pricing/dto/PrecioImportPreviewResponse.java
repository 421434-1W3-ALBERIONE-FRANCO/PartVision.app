package com.partvision.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PrecioImportPreviewResponse(
        List<PreviewFila> filas,
        int total,
        int ok,
        int conflictos,
        int noEncontrados,
        BigDecimal margenAplicado
) {
    public record PreviewFila(
            int fila,
            String skuCsv,
            BigDecimal precioCostoCsv,
            String estado,
            Long productoId,
            String productoDescripcion,
            String productoMarca,
            BigDecimal precioActual,
            BigDecimal precioNuevoCalculado,
            int cantidadMatches
    ) {}
}
