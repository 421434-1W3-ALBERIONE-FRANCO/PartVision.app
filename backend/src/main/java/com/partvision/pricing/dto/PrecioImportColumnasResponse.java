package com.partvision.pricing.dto;

import java.util.List;

public record PrecioImportColumnasResponse(
        String uploadId,
        List<String> columnas,
        int totalFilas
) {}
