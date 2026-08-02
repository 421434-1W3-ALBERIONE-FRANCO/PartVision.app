package com.partvision.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarcaRequest(
        @NotBlank @Size(max = 150) String nombre
) {
}
