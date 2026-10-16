package com.partvision.catalog.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoProductoTest {

    /** Los casos de la lista de EGSA del 2026-10-15: un guion o un punto de mas. */
    @Test
    void clave_sacaEspaciosPuntosGuionesYBarras() {
        assertThat(CodigoProducto.clave("PK-2000")).isEqualTo("PK2000");
        assertThat(CodigoProducto.clave("PKC-0097-060")).isEqualTo(CodigoProducto.clave("PKC0097-060"));
        assertThat(CodigoProducto.clave("10.272")).isEqualTo("10272");
        assertThat(CodigoProducto.clave("A205/2STD")).isEqualTo("A2052STD");
        assertThat(CodigoProducto.clave(" jt 1232 ")).isEqualTo("JT1232");
    }

    /** El asterisco y los parentesis distinguen medidas y versiones: se conservan. */
    @Test
    void clave_conservaLoQueDistingueUnProductoDeOtro() {
        assertThat(CodigoProducto.clave("JJT2NR-FE*N*AK")).isEqualTo("JJT2NRFE*N*AK");
        assertThat(CodigoProducto.clave("813667(STD)")).isEqualTo("813667(STD)");
    }

    @Test
    void clave_deNadaEsNada() {
        assertThat(CodigoProducto.clave(null)).isNull();
    }
}
