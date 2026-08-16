package com.partvision.auth.dto;

import java.util.List;

/** Códigos de recuperación de un solo uso. Se muestran UNA sola vez, al activar el 2FA. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
