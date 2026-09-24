package com.partvision.compras.dto;

import com.partvision.compras.domain.CompraEstado;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de estado hecho a mano desde el panel, sin esperar a la planilla. No incluye
 * INGRESADA: ese estado necesita una ubicacion por linea para cargar el stock, y va por
 * {@code PATCH /compras/{id}/ingresar}.
 */
public record CambiarEstadoManualRequest(
        @NotNull CompraEstado estado
) {
}
