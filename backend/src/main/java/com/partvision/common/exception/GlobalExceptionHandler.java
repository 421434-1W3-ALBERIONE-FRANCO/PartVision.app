package com.partvision.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Manejo centralizado de excepciones: transforma cada error en un
 * {@link ApiError} con formato uniforme. Ningun controller o service
 * necesita bloques try-catch para responder errores HTTP.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    /**
     * Login correcto de usuario/contraseña pero falta el código 2FA. Devuelve 401 con la marca
     * {@code twoFactorRequired: true} para que el frontend muestre el campo del código.
     */
    @ExceptionHandler(TwoFactorRequiredException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleTwoFactorRequired(
            TwoFactorRequiredException ex, HttpServletRequest request) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("twoFactorRequired", true);
        body.put("message", ex.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler({
            org.springframework.security.access.AccessDeniedException.class,
            org.springframework.security.authorization.AuthorizationDeniedException.class
    })
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acceso denegado: no tenés permisos para realizar esta acción", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Violacion de integridad en {}: {}", request.getRequestURI(), detail);
        String msg = "Viola una restriccion de integridad de datos";
        if (detail != null && detail.contains("duplicate key")) {
            msg += ": registro duplicado";
        }
        if (detail != null) {
            msg += " (" + detail.substring(0, Math.min(detail.length(), 200)) + ")";
        }
        return build(HttpStatus.CONFLICT, msg, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldValidationError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiError.FieldValidationError> details = ex.getConstraintViolations().stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Datos de entrada invalidos", request, details);
    }

    /** Ruta inexistente (no hay handler ni recurso). Devuelve 404, no 500. */
    @ExceptionHandler({
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + request.getRequestURI(), request, null);
    }

    /** Método HTTP no soportado por la ruta (p. ej. PATCH donde solo hay POST). Devuelve 405. */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido: " + ex.getMethod(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<ApiError> handleIOException(java.io.IOException ex, HttpServletRequest request) {
        log.warn("Error de I/O en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Error al procesar el archivo: " + ex.getMessage(), request, null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(org.springframework.web.multipart.MultipartException ex,
                                                     HttpServletRequest request) {
        log.warn("Error multipart en {}: {}", request.getRequestURI(), ex.getMessage());
        String msg = ex.getMessage() != null && ex.getMessage().contains("size")
                ? "El archivo es demasiado grande (máximo 15MB)"
                : "Error al procesar el archivo subido: " + ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(
            org.springframework.web.multipart.support.MissingServletRequestPartException ex,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Falta el archivo: " + ex.getRequestPartName(), request, null);
    }

    @ExceptionHandler(jakarta.servlet.ServletException.class)
    public ResponseEntity<ApiError> handleServletException(jakarta.servlet.ServletException ex,
                                                            HttpServletRequest request) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        log.error("ServletException en {} — root: [{}] {}", request.getRequestURI(),
                root.getClass().getName(), root.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error de servlet: " + root.getClass().getSimpleName() + " — " + root.getMessage(), request, null);
    }

    /**
     * Excepciones que ya traen su propio status (p. ej. el 401 de una API key invalida
     * o el 503 de un endpoint sin configurar). Sin esto caian en el handler generico
     * y salian como 500.
     */
    @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
    public ResponseEntity<ApiError> handleErrorResponse(org.springframework.web.ErrorResponseException ex,
                                                         HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String msg = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : status.getReasonPhrase();
        return build(status, msg, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        log.error("Error no controlado en {} [{}]: {} — root: [{}] {}", request.getRequestURI(),
                ex.getClass().getName(), ex.getMessage(), root.getClass().getName(), root.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error: " + root.getClass().getSimpleName() + " — " + root.getMessage(), request, null);
    }

    private ApiError.FieldValidationError toFieldError(FieldError fieldError) {
        return new ApiError.FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ApiError.FieldValidationError toFieldError(ConstraintViolation<?> violation) {
        return new ApiError.FieldValidationError(violation.getPropertyPath().toString(), violation.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           List<ApiError.FieldValidationError> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details);
        return ResponseEntity.status(status).body(body);
    }
}
