package com.partvision.compras.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull Long ubicacionId
) {}
