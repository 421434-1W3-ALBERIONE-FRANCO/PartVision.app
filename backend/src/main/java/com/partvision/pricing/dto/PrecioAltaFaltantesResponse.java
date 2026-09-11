package com.partvision.pricing.dto;

public record PrecioAltaFaltantesResponse(
        int creados,
        int omitidos,
        String mensaje
) {}
