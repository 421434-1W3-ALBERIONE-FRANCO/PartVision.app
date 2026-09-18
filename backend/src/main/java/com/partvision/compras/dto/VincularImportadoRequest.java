package com.partvision.compras.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Asociar una linea importada a un producto que ya esta en el catalogo: la misma pieza pedida
 * de nuevo. {@code ubicacionId}, igual que en el alta, solo si la compra ya ingreso.
 */
public record VincularImportadoRequest(
        @NotNull Long productoId,
        Long ubicacionId
) {
}
