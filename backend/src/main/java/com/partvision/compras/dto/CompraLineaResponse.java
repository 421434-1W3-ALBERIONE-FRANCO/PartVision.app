package com.partvision.compras.dto;

import com.partvision.compras.domain.CompraLinea;

public record CompraLineaResponse(
        Long id,
        String codigo,
        String descripcion,
        int cantidad,
        Long productoId,
        String productoDescripcion,
        String productoMarca
) {
    public static CompraLineaResponse from(CompraLinea l) {
        return new CompraLineaResponse(
                l.getId(),
                l.getCodigo(),
                l.getDescripcion(),
                l.getCantidad(),
                l.getProducto() != null ? l.getProducto().getId() : null,
                l.getProducto() != null ? l.getProducto().getDescripcion() : null,
                l.getProducto() != null && l.getProducto().getMarca() != null
                        ? l.getProducto().getMarca().getNombre() : null
        );
    }
}
