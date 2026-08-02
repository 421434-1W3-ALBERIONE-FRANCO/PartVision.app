package com.partvision.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    void mensajeExplicito() {
        var ex = new ResourceNotFoundException("El recurso no existe");
        assertThat(ex.getMessage()).isEqualTo("El recurso no existe");
    }

    @Test
    void mensajeConRecursoEId() {
        var ex = new ResourceNotFoundException("Producto", 42L);
        assertThat(ex.getMessage()).isEqualTo("Producto no encontrado: 42");
    }
}
