package com.partvision.ai.vision;

/**
 * Puerto para extraer informacion estructurada de una imagen mediante IA de vision.
 * La implementacion real (Claude / OpenAI con Structured Outputs) se conecta detras
 * de esta interfaz; el backend SIEMPRE valida el resultado y nunca confia ciegamente.
 */
public interface VisionExtractor {

    ExtraccionIA extraer(byte[] imagen, String contentType);
}
