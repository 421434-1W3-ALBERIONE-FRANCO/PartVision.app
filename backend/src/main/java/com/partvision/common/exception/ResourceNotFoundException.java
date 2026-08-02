package com.partvision.common.exception;

/**
 * Se lanza cuando un recurso solicitado no existe. -> HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super("%s no encontrado: %s".formatted(resource, id));
    }
}
