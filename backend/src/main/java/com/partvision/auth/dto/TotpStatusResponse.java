package com.partvision.auth.dto;

/** Estado del 2FA del usuario actual (para que el frontend muestre activar/desactivar). */
public record TotpStatusResponse(boolean enabled) {
}
