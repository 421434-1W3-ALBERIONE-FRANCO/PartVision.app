package com.partvision.catalog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductoMatcherTest {

    private final ProductoMatcher matcher = new ProductoMatcher();

    @Test
    void normalizar_dejaSoloAlfanumericosEnMayuscula() {
        assertThat(matcher.normalizar("81 3667+0.5")).isEqualTo("81366705");
        assertThat(matcher.normalizar("813667(05)")).isEqualTo("81366705");
        assertThat(matcher.normalizar("JAVAEB0*K754")).isEqualTo("JAVAEB0K754");
        assertThat(matcher.normalizar(null)).isEmpty();
        assertThat(matcher.normalizar("  ")).isEmpty();
    }

    @Test
    void coincideExacto_ignoraFormatoPeroRespetaLaMedida() {
        // Mismo producto con distinto formato (espacio / parentesis / "+").
        assertThat(matcher.coincideExacto("81 3667+0.5", "813667(05)")).isTrue();
        assertThat(matcher.coincideExacto("80 9037 STD", "809037(STD)")).isTrue();
        assertThat(matcher.coincideExacto("JAVAEB0*K754", "JAVAEB0*K754")).isTrue();

        // Distinta medida (STD vs +0.5): NO deben colapsar.
        assertThat(matcher.coincideExacto("813667(05)", "813667(STD)")).isFalse();
        assertThat(matcher.coincideExacto("80 9037 STD", "819037(STD)")).isFalse();
    }

    @Test
    void coincideExacto_vacioNuncaMatchea() {
        assertThat(matcher.coincideExacto(null, "813667")).isFalse();
        assertThat(matcher.coincideExacto("()", "----")).isFalse();
    }

    @Test
    void anclaBusqueda_tomaLaBaseParaElRecall() {
        assertThat(matcher.anclaBusqueda("81 3667+0.5")).isEqualTo("813667");
        assertThat(matcher.anclaBusqueda("80 9037 STD")).isEqualTo("809037");
        assertThat(matcher.anclaBusqueda("JAVAEB0*K754")).isEqualTo("JAVAEB0");
        assertThat(matcher.anclaBusqueda("813667(STD)")).isEqualTo("813667");
        assertThat(matcher.anclaBusqueda(null)).isEmpty();
        assertThat(matcher.anclaBusqueda("+++")).isEmpty();
    }

    @Test
    void marcaCompatible_soloRechazaConflictoExplicito() {
        assertThat(matcher.marcaCompatible("POWER ENGINE", "power engine")).isTrue();
        assertThat(matcher.marcaCompatible(null, "AKURO")).isTrue();
        assertThat(matcher.marcaCompatible("AKURO", null)).isTrue();
        assertThat(matcher.marcaCompatible("", "AKURO")).isTrue();
        assertThat(matcher.marcaCompatible("POWER ENGINE", "AKURO")).isFalse();
    }
}
