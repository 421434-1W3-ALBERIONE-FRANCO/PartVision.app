package com.partvision.common.exception;

/**
 * Se lanza al intentar crear un recurso que viola una restriccion de
 * unicidad de negocio (ej: SKU o codigo de barras ya existente). -> HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
