package com.partvision.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo de respuesta uniforme para todos los errores de la API.
 *
 * @param timestamp momento del error
 * @param status    codigo HTTP
 * @param error     nombre corto del estado HTTP
 * @param message   mensaje legible para el cliente
 * @param path      ruta que origino el error
 * @param details   errores de validacion campo a campo (opcional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldValidationError> details
) {
    public record FieldValidationError(String field, String message) {
    }
}
