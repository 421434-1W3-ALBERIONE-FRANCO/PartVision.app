package com.partvision.pricing.dto;

import com.partvision.pricing.domain.PrecioRevision;

import java.math.BigDecimal;

public record PrecioRevisionResponse(
        long id,
        long productoId,
        String sku,
        String descripcion,
        BigDecimal costoActual,
        BigDecimal costoNuevo,
        BigDecimal variacionPct
) {
    public static PrecioRevisionResponse from(PrecioRevision r) {
        return new PrecioRevisionResponse(r.getId(), r.getProducto().getId(), r.getProducto().getSku(),
                r.getProducto().getDescripcion(), r.getCostoActual(), r.getCostoNuevo(), r.getVariacionPct());
    }
}
