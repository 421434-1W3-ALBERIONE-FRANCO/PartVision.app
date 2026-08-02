package com.partvision.catalog.dto;

import com.partvision.catalog.domain.ProductoCodigo;

public record ProductoCodigoResponse(Long id, String codigo, String tipo) {

    public static ProductoCodigoResponse from(ProductoCodigo codigo) {
        return new ProductoCodigoResponse(codigo.getId(), codigo.getCodigo(), codigo.getTipo());
    }
}
