package com.partvision.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Conteo físico: fija la cantidad REAL de un producto en una ubicación. El sistema
 * calcula solo la diferencia contra lo registrado y genera el ajuste correspondiente
 * (positivo, negativo o ninguno si ya coincidía). Es la carga de existencias reales
 * que hace el usuario final al recorrer el depósito.
 */
public record ConteoRequest(
        @NotNull Long productoId,
        @NotNull Long ubicacionId,
        @NotNull @Min(0) Integer cantidadReal,
        @Size(max = 300) String motivo
) {
}
