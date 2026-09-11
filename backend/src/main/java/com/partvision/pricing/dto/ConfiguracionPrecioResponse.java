package com.partvision.pricing.dto;

import com.partvision.pricing.domain.ConfiguracionPrecio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConfiguracionPrecioResponse(
        Long id,
        String proveedor,
        BigDecimal margen,
        BigDecimal ajusteLista,
        boolean activo,
        LocalDateTime updatedAt
) {
    public static ConfiguracionPrecioResponse from(ConfiguracionPrecio e) {
        return new ConfiguracionPrecioResponse(e.getId(), e.getProveedor(), e.getMargen(),
                e.getAjusteLista(), e.isActivo(), e.getUpdatedAt());
    }
}
