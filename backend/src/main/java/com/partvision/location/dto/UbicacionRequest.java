package com.partvision.location.dto;

import com.partvision.location.domain.TipoUbicacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UbicacionRequest(
        @NotNull TipoUbicacion tipo,
        @NotBlank @Size(max = 50) String codigo,
        Long parentId
) {
}
