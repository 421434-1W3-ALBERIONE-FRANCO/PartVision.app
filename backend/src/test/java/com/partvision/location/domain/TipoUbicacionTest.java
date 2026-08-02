package com.partvision.location.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoUbicacionTest {

    @Test
    void profundidadCreceConLaJerarquia() {
        assertThat(TipoUbicacion.DEPOSITO.getProfundidad()).isLessThan(TipoUbicacion.SECTOR.getProfundidad());
        assertThat(TipoUbicacion.NIVEL.getProfundidad()).isEqualTo(4);
        assertThat(TipoUbicacion.values()).hasSize(5);
        assertThat(TipoUbicacion.valueOf("PASILLO")).isEqualTo(TipoUbicacion.PASILLO);
    }
}
