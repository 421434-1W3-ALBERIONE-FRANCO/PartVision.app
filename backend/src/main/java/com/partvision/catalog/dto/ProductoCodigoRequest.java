package com.partvision.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductoCodigoRequest(
        @NotBlank @Size(max = 100) String codigo,
        @Size(max = 30) String tipo
) {
}
