package com.partvision.pricing.dto;

public record SyncResultResponse(
        int totalProductos,
        int actualizados,
        int noEncontrados,
        int errores,
        String mensaje
) {}
