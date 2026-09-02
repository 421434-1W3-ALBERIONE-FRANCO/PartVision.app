package com.partvision.compras.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecepcionLineaRequest(
        @NotBlank @Size(max = 100) String codigo,
        @Size(max = 500) String descripcion,
        @Min(1) int cantidad
) {}
