package com.partvision.compras.dto;

import com.partvision.compras.domain.RevisionLinea;
import jakarta.validation.constraints.NotNull;

/** {@code ACEPTADA} o {@code DESCARTADA}. {@code PENDIENTE} no es una decision y se rechaza. */
public record RevisionLineaRequest(
        @NotNull RevisionLinea decision
) {
}
