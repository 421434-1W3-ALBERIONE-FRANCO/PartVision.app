package com.partvision.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CambiarEstadoRequest(
        @NotEmpty @Valid List<LineaUbicacion> asignaciones
) {
    public record LineaUbicacion(
            @NotNull Long lineaId,
            @NotNull Long ubicacionId
    ) {}
}
