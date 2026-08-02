package com.partvision.ai.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoExtraccionTest {

    @Test
    void tieneLosEstadosEsperados() {
        assertThat(EstadoExtraccion.values())
                .containsExactly(EstadoExtraccion.PENDIENTE, EstadoExtraccion.CONFIRMADA, EstadoExtraccion.DESCARTADA);
        assertThat(EstadoExtraccion.valueOf("CONFIRMADA")).isEqualTo(EstadoExtraccion.CONFIRMADA);
    }
}
