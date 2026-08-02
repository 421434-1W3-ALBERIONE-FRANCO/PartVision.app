package com.partvision.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller ficticio (solo test) para ejercitar el {@link GlobalExceptionHandler}
 * a traves de la capa MVC real.
 */
@RestController
@Validated
@RequestMapping("/test-errors")
class TestExceptionController {

    record SampleRequest(@NotBlank(message = "no debe estar vacio") String nombre) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new ResourceNotFoundException("Producto", 1L);
    }

    @GetMapping("/duplicate")
    void duplicate() {
        throw new DuplicateResourceException("SKU duplicado");
    }

    @GetMapping("/business")
    void business() {
        throw new BusinessException("Stock insuficiente");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("fallo inesperado");
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody SampleRequest request) {
        // el cuerpo no importa: si es invalido, dispara MethodArgumentNotValidException
    }
}
