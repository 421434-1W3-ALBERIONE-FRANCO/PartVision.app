package com.partvision.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalidaRequest(
        @NotNull Long productoId,
        @NotNull Long ubicacionId,
        @NotNull @Min(1) Integer cantidad,
        @Size(max = 300) String motivo
) {
}
