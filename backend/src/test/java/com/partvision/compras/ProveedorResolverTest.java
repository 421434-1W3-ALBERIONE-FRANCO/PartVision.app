package com.partvision.compras;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProveedorResolverTest {

    @Test
    void alias_llevaAlNombreDelCatalogo() {
        ProveedorResolver r = new ProveedorResolver("ADS=Autopartes del Sur");

        assertThat(r.resolver("ADS")).isEqualTo("Autopartes del Sur");
        assertThat(r.resolver(" ads ")).isEqualTo("Autopartes del Sur");
    }

    @Test
    void sinAlias_quedaElTextoRecortado() {
        ProveedorResolver r = new ProveedorResolver("ADS=Autopartes del Sur");

        assertThat(r.resolver("  EGSA ")).isEqualTo("EGSA");
    }

    @Test
    void vacio_esNull() {
        ProveedorResolver r = new ProveedorResolver("ADS=Autopartes del Sur");

        assertThat(r.resolver(null)).isNull();
        assertThat(r.resolver("   ")).isNull();
    }

    @Test
    void variosAlias_yClavesConTildesOEspacios() {
        ProveedorResolver r = new ProveedorResolver(
                "ADS=Autopartes del Sur; Autopartes Súr SA =Autopartes del Sur;E.G.S.A.=EGSA");

        assertThat(r.resolver("AUTOPARTES SUR SA")).isEqualTo("Autopartes del Sur");
        assertThat(r.resolver("e.g.s.a.")).isEqualTo("EGSA");
    }

    /** Una configuracion mal escrita no puede tirar el arranque: se ignora esa entrada. */
    @Test
    void entradasMalFormadas_seIgnoran() {
        ProveedorResolver r = new ProveedorResolver("sin-igual;=SinClave;SinNombre=;  =X;ADS=Autopartes del Sur");

        assertThat(r.resolver("ADS")).isEqualTo("Autopartes del Sur");
        assertThat(r.resolver("SinNombre")).isEqualTo("SinNombre");
        assertThat(r.resolver("sin-igual")).isEqualTo("sin-igual");
    }

    @Test
    void configuracionVacia_noTieneAlias() {
        assertThat(new ProveedorResolver("").resolver("ADS")).isEqualTo("ADS");
    }
}
