package com.partvision.location.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoUbicacionTest {

    @Test
    void profundidadCreceConLaJerarquia() {
        assertThat(TipoUbicacion.DEPOSITO.getProfundidad()).isLessThan(TipoUbicacion.PASILLO.getProfundidad());
        assertThat(TipoUbicacion.ESTANTE.getProfundidad()).isEqualTo(TipoUbicacion.PALLET.getProfundidad());
        assertThat(TipoUbicacion.OTRO.getProfundidad()).isEqualTo(4);
        assertThat(TipoUbicacion.values()).hasSize(6);
        assertThat(TipoUbicacion.valueOf("ESTANTE")).isEqualTo(TipoUbicacion.ESTANTE);
    }
}
