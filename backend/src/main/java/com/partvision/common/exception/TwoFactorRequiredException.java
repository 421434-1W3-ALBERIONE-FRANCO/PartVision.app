package com.partvision.common.exception;

/**
 * Se lanza en el login cuando el usuario tiene 2FA activo y todavía no envió el código del
 * authenticator. El {@code GlobalExceptionHandler} la traduce a una respuesta con la marca
 * {@code twoFactorRequired: true} para que el frontend pida el código.
 */
public class TwoFactorRequiredException extends RuntimeException {

    public TwoFactorRequiredException(String message) {
        super(message);
    }
}
