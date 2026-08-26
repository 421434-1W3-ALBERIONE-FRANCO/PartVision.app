package com.partvision.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConfiguracionPrecioRequest(
        @NotNull @DecimalMin("0") BigDecimal margen,
        Boolean activo
) {}
