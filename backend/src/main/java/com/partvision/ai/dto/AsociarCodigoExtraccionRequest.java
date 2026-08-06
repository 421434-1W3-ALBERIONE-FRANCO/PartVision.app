package com.partvision.ai.dto;

import jakarta.validation.constraints.NotNull;

/** Asocia el codigo de barras detectado por una extraccion a un producto existente. */
public record AsociarCodigoExtraccionRequest(
        @NotNull Long productoId
) {
}
