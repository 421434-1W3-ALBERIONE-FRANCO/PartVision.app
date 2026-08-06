package com.partvision.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Conteo físico de varias líneas a la vez (carga de existencias en lote). */
public record ConteoLoteRequest(
        @NotEmpty @Valid List<ConteoRequest> conteos
) {
}
