package com.partvision.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferenciaRequest(
        @NotNull Long productoId,
        @NotNull Long ubicacionOrigenId,
        @NotNull Long ubicacionDestinoId,
        @NotNull @Min(1) Integer cantidad,
        @Size(max = 300) String motivo
) {
}
