package com.partvision.pricing.dto;

public record PrecioImportProgresoResponse(
        boolean importando,
        int progreso,
        int total,
        PrecioImportResultResponse ultimoResultado
) {}
