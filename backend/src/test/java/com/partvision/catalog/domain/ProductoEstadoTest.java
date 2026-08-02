package com.partvision.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductoEstadoTest {

    @Test
    void contieneLosEstadosEsperados() {
        assertThat(ProductoEstado.values())
                .containsExactly(ProductoEstado.BORRADOR, ProductoEstado.ACTIVO, ProductoEstado.INACTIVO);
        assertThat(ProductoEstado.valueOf("ACTIVO")).isEqualTo(ProductoEstado.ACTIVO);
    }
}
