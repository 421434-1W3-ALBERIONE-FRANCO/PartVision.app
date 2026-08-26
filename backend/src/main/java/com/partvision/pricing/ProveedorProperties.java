package com.partvision.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "partvision.proveedor")
public record ProveedorProperties(
        String url,
        String username,
        String password,
        BigDecimal margen
) {
    public BigDecimal multiplicadorMargen() {
        return BigDecimal.ONE.add(margen.divide(BigDecimal.valueOf(100)));
    }
}
