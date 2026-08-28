package com.partvision.pricing.dto;

import com.partvision.pricing.domain.ImportPrecioBatch;

import java.time.LocalDateTime;

public record PrecioBatchResponse(
        long id,
        String proveedor,
        String fuente,
        String archivo,
        int total,
        int aplicados,
        int conflictos,
        String estado,
        LocalDateTime createdAt
) {
    public static PrecioBatchResponse from(ImportPrecioBatch b) {
        return new PrecioBatchResponse(b.getId(), b.getProveedor(), b.getFuente(),
                b.getArchivo(), b.getTotal(), b.getAplicados(), b.getConflictos(),
                b.getEstado(), b.getCreatedAt());
    }
}
