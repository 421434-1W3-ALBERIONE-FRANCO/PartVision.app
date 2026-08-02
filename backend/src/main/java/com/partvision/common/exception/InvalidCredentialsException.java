package com.partvision.common.exception;

/**
 * Se lanza cuando fallan las credenciales o el usuario no puede autenticarse.
 * -> HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
