package com.partvision.ai.vision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StubVisionExtractorTest {

    @Test
    void noInventaDatos_todoNull() {
        ExtraccionIA r = new StubVisionExtractor().extraer(new byte[]{1}, "image/jpeg");

        assertThat(r.codigoPieza()).isNull();
        assertThat(r.marca()).isNull();
        assertThat(r.descripcion()).isNull();
        assertThat(r.codigoBarras()).isNull();
        assertThat(r.detallesExtra()).isEmpty();
        assertThat(r.modelo()).isEqualTo("stub-vision");
    }
}
