package com.partvision.ai.dto;

import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.inventory.dto.MovimientoResponse;

/**
 * Resultado de confirmar una extraccion: el producto creado y, si se cargo stock
 * inicial, el movimiento generado (null si no se cargo stock).
 */
public record ConfirmacionResponse(
        Long extraccionId,
        ProductoResponse producto,
        MovimientoResponse movimiento
) {
}
