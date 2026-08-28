package com.partvision.pricing.dto;

public record PrecioImportResultResponse(
        long batchId,
        int total,
        int aplicados,
        int omitidos,
        int conflictos,
        String mensaje
) {}
