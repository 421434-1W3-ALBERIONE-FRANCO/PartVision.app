package com.partvision.ai.vision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Implementacion por defecto (no inventa nada: devuelve todo en null, el operario
 * completa los datos en la revision). Activa salvo que se configure otro proveedor
 * con {@code ai.vision.provider} (ej: "claude" -> {@link ClaudeVisionExtractor}).
 */
@Component
@ConditionalOnProperty(name = "ai.vision.provider", havingValue = "stub", matchIfMissing = true)
public class StubVisionExtractor implements VisionExtractor {

    @Override
    public ExtraccionIA extraer(byte[] imagen, String contentType) {
        return new ExtraccionIA(null, null, null, null, Map.of(), "stub-vision");
    }
}
