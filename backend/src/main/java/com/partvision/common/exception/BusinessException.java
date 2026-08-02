package com.partvision.common.exception;

/**
 * Se lanza cuando una operacion viola una regla de negocio
 * (ej: stock insuficiente para una salida). -> HTTP 422.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
