package com.partvision.ai.vision;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Implementacion por defecto (placeholder) hasta enchufar un proveedor real de
 * vision. Devuelve todo en null (no inventa nada): el operario completa los datos
 * en la revision. Cuando exista un extractor real, marcarlo como {@code @Primary}
 * (o activarlo por perfil) para que reemplace a este stub.
 */
@Component
public class StubVisionExtractor implements VisionExtractor {

    @Override
    public ExtraccionIA extraer(byte[] imagen, String contentType) {
        return new ExtraccionIA(null, null, null, null, Map.of(), "stub-vision");
    }
}
