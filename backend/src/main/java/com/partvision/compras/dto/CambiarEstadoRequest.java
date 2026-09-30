package com.partvision.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CambiarEstadoRequest(
        /**
         * Las lineas que se ingresan en esta pasada. Puede ser una parte: la compra queda
         * POR_UBICAR hasta que no le falte ninguna. Vacia sirve para cerrar una compra a la
         * que ya no le queda nada que ubicar.
         */
        @NotNull @Valid List<LineaUbicacion> asignaciones
) {
    public record LineaUbicacion(
            @NotNull Long lineaId,
            @NotNull Long ubicacionId
    ) {}
}
