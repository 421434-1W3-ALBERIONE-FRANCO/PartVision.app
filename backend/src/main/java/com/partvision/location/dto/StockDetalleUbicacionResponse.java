package com.partvision.location.dto;

public record StockDetalleUbicacionResponse(
        Long productoId,
        String sku,
        String descripcion,
        String marcaNombre,
        String categoriaNombre,
        int cantidad
) {}
