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

    /**
     * Lo unico que ve el cliente ante un error no previsto. El detalle —clase y mensaje de
     * la causa raiz— queda en el log del servidor: devolverlo le describe las internas de
     * la app a cualquiera que provoque un 500, incluso sin estar autenticado.
     */
    private static final String MENSAJE_GENERICO = "Error interno del servidor";

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

    /**
     * El cuerpo falta o no es el JSON esperado. Caia en el handler generico y salia 500:
     * ademas de ser mentira (el error es del que llama), un 5xx hace que Power Automate
     * reintente cuatro veces un pedido que nunca va a funcionar.
     *
     * <p>Separa las dos causas a proposito. El 2026-09-24 el flujo del cliente mandaba el pedido
     * sin cuerpo mientras Power Automate mostraba el cuerpo en "Entradas": con un unico mensaje
     * para las dos causas, la pantalla del flujo no alcanzaba para saber si el JSON venia mal o
     * si directamente no venia, y hubo que leer los logs del servidor para distinguirlo. Cuando
     * falta, el mensaje describe COMO llego el pedido —cabeceras de transporte, que son del que
     * llama, no internas nuestras—, que es lo unico que permite encontrar la causa desde afuera.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleCuerpoIlegible(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        String causa = ex.getMostSpecificCause().getMessage();
        if (causa == null || !causa.startsWith(SIN_CUERPO)) {
            log.warn("Cuerpo ilegible en {}: {}", request.getRequestURI(), causa);
            return build(HttpStatus.BAD_REQUEST,
                    "El cuerpo del pedido no es un JSON que se pueda leer", request, null);
        }
        String transporte = comoLlego(request);
        log.warn("Pedido sin cuerpo en {} ({})", request.getRequestURI(), transporte);
        String mensaje = "El pedido llego sin cuerpo (" + transporte + ")";
        if (sanear(request.getHeader(TRANSFERENCIA_FRAGMENTADA)) != null) {
            mensaje += ". El flujo esta mandando el cuerpo fragmentado: desactiva"
                    + " la fragmentacion en la configuracion de la accion HTTP";
        }
        return build(HttpStatus.BAD_REQUEST, mensaje, request, null);
    }

    /** Lo que Spring dice cuando el stream del pedido venia vacio. */
    private static final String SIN_CUERPO = "Required request body is missing";

    /** Con esta cabecera Power Automate anuncia que el cuerpo va aparte, en pedidos sucesivos. */
    private static final String TRANSFERENCIA_FRAGMENTADA = "x-ms-transfer-mode";

    /**
     * Las cabeceras de transporte del pedido. Vienen del que llama, asi que se recortan y se
     * dejan solo caracteres imprimibles antes de devolverlas.
     */
    private String comoLlego(HttpServletRequest request) {
        StringBuilder detalle = new StringBuilder("Content-Length: ")
                .append(request.getContentLengthLong());
        for (String nombre : List.of("Transfer-Encoding", TRANSFERENCIA_FRAGMENTADA)) {
            String valor = sanear(request.getHeader(nombre));
            if (valor != null) {
                detalle.append(", ").append(nombre).append(": ").append(valor);
            }
        }
        return detalle.toString();
    }

    private String sanear(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("[^ -~]", "").trim();
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > 40 ? limpio.substring(0, 40) : limpio;
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
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_GENERICO, request, null);
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
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_GENERICO, request, null);
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
