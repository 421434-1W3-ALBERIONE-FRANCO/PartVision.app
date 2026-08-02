package com.partvision.ai.dto;

import com.partvision.ai.domain.AiExtraction;
import com.partvision.ai.domain.EstadoExtraccion;

import java.util.Map;

public record AiExtractionResponse(
        Long id,
        String imagenKey,
        String modelo,
        EstadoExtraccion estado,
        Map<String, Object> datosSugeridos,
        Long productoId
) {
    public static AiExtractionResponse from(AiExtraction e) {
        return new AiExtractionResponse(
                e.getId(),
                e.getImagenKey(),
                e.getModelo(),
                e.getEstado(),
                e.getDatosSugeridos(),
                e.getProductoId());
    }
}
