package com.partvision.ai.dto;

import com.partvision.catalog.dto.ProductoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Confirmacion de una extraccion: los datos revisados/corregidos del producto y,
 * opcionalmente, una entrada de stock inicial (cantidad + ubicacion juntas).
 */
public record ConfirmarExtraccionRequest(
        @NotNull @Valid ProductoRequest producto,
        Long ubicacionId,
        @Min(1) Integer cantidad
) {
}
