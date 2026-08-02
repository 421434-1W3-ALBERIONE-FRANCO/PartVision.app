package com.partvision.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La ruta de {@link ConstraintViolationException} (validacion de parametros de
 * metodo) se prueba de forma directa para no depender del detalle interno de
 * validacion de metodos de Spring MVC.
 */
class GlobalExceptionHandlerConstraintTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void constraintViolation_devuelve400ConDetalle() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("cantidad");

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("debe ser mayor a 0");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/stock");

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.path()).isEqualTo("/api/v1/stock");
        assertThat(body.details()).hasSize(1);
        assertThat(body.details().get(0).field()).isEqualTo("cantidad");
        assertThat(body.details().get(0).message()).isEqualTo("debe ser mayor a 0");
    }
}
