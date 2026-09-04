package com.partvision.compras.dto;

import com.partvision.compras.domain.CompraLinea;

public record CompraLineaResponse(
        Long id,
        String codigo,
        String descripcion,
        int cantidad,
        Long productoId,
        String productoDescripcion,
        String productoMarca,
        Long ubicacionSugeridaId,
        String ubicacionSugeridaCodigo,
        Long ubicacionIngresoId,
        String ubicacionIngresoCodigo
) {
    public static CompraLineaResponse from(CompraLinea l) {
        return from(l, null, null);
    }

    public static CompraLineaResponse from(CompraLinea l, Long ubicSugeridaId, String ubicSugeridaCodigo) {
        return new CompraLineaResponse(
                l.getId(),
                l.getCodigo(),
                l.getDescripcion(),
                l.getCantidad(),
                l.getProducto() != null ? l.getProducto().getId() : null,
                l.getProducto() != null ? l.getProducto().getDescripcion() : null,
                l.getProducto() != null && l.getProducto().getMarca() != null
                        ? l.getProducto().getMarca().getNombre() : null,
                ubicSugeridaId,
                ubicSugeridaCodigo,
                l.getUbicacionIngreso() != null ? l.getUbicacionIngreso().getId() : null,
                l.getUbicacionIngreso() != null ? l.getUbicacionIngreso().getCodigo() : null
        );
    }
}
