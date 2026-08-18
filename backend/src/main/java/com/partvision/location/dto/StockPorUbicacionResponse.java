package com.partvision.location.dto;

public record StockPorUbicacionResponse(
        Long ubicacionId,
        long cantidadTotal,
        long productosDistintos
) {}
