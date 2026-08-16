package com.partvision.auth.dto;

/**
 * Respuesta del inicio de configuración de 2FA. El {@code otpauthUri} se renderiza como QR
 * en el frontend; el {@code secret} se muestra por si el usuario prefiere cargarlo a mano.
 */
public record TotpSetupResponse(String secret, String otpauthUri) {
}
