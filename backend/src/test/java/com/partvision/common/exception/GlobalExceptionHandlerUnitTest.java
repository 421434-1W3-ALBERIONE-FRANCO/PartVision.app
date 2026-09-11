package com.partvision.common.exception;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ejercita los handlers directamente. Varios solo se alcanzan con excepciones que el
 * contenedor lanza antes de llegar a un controller, y son incomodas de provocar por MockMvc.
 */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/algo");
        return request;
    }

    @Test
    void integridad_sinClaveDuplicada_noAgregaEseTexto() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("null value in column \"sku\""), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .contains("restriccion de integridad")
                .doesNotContain("registro duplicado");
    }

    @Test
    void integridad_detalleLargo_seRecortaA200() {
        String largo = "duplicate key ".repeat(40);

        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException(largo), request());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("registro duplicado");
        assertThat(response.getBody().message().length()).isLessThan(largo.length());
    }

    @Test
    void integridad_sinDetalle_noRompe() {
        ResponseEntity<ApiError> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException(null), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Viola una restriccion de integridad de datos");
    }

    @Test
    void rutaInexistente_devuelve404ConLaRuta() {
        ResponseEntity<ApiError> response = handler.handleNoHandler(
                new NoHandlerFoundException("GET", "/api/v1/algo", null), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("/api/v1/algo");
    }

    @Test
    void metodoNoSoportado_devuelve405() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("PATCH");
    }

    @Test
    void argumentoInvalido_devuelve400() {
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("id negativo"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("id negativo");
    }

    @Test
    void errorDeIO_devuelve400() {
        ResponseEntity<ApiError> response = handler.handleIOException(
                new IOException("disco lleno"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("disco lleno");
    }

    @Test
    void multipartPorTamano_avisaDelLimite() {
        ResponseEntity<ApiError> response = handler.handleMultipart(
                new MaxUploadSizeExceededException(15_728_640L), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("demasiado grande");
    }

    @Test
    void multipartPorOtraCausa_devuelveElDetalle() {
        ResponseEntity<ApiError> response = handler.handleMultipart(
                new MultipartException("borde mal formado"), request());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("borde mal formado");
    }

    @Test
    void multipartSinMensaje_noRompe() {
        ResponseEntity<ApiError> response = handler.handleMultipart(
                new MultipartException(null), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void faltaElArchivo_diceCual() {
        ResponseEntity<ApiError> response = handler.handleMissingPart(
                new MissingServletRequestPartException("archivo"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("archivo");
    }

    /** El cliente recibe un mensaje generico; el detalle queda en el log. */
    @Test
    void servletException_devuelve500Generico() {
        ResponseEntity<ApiError> response = handler.handleServletException(
                new ServletException(new IllegalStateException("pool agotado")), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
        assertThat(response.getBody().message()).doesNotContain("pool agotado");
    }

    @Test
    void excepcionSinCausa_tambienDevuelveElGenerico() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("detalle interno"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
    }

    /** Un ResponseStatusException tiene que conservar SU status, no degradar a 500. */
    @Test
    void errorResponse_conservaElStatusYElDetalle() {
        ResponseEntity<ApiError> response = handler.handleErrorResponse(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key invalida"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("API key invalida");
    }

    @Test
    void errorResponse_sinDetalle_usaElTextoDelStatus() {
        ResponseEntity<ApiError> response = handler.handleErrorResponse(
                new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Service Unavailable");
    }
}
