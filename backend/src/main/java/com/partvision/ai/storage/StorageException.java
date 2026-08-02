package com.partvision.ai.storage;

/**
 * Falla al almacenar/recuperar un archivo. -> HTTP 500 (error de infraestructura).
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
