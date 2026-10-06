package com.partvision.pricing;

import com.partvision.common.exception.BusinessException;
import com.partvision.pricing.domain.OrigenSincronizacion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FuentesListasTest {

    private final AdsSyncProperties ads = new AdsSyncProperties("u", "p", "http://ads.test", "Autopartes del Sur",
            "Código", "Precio de Lista", 60, 35, 10, 80, 1000, 5, 50, 3, 5, 5, 1);
    private final EgsaRecepcionProperties egsa = SincronizacionPreciosServiceTest.egsaProps();
    private final FuentesListas fuentes = new FuentesListas(ads, egsa);

    @Test
    void sinDecirCual_esAds() {
        assertThat(fuentes.de(null).proveedor()).isEqualTo("Autopartes del Sur");
        assertThat(fuentes.de("  ").nombre()).isEqualTo("ADS");
    }

    @Test
    void sinImportarMayusculasNiBordes() {
        assertThat(fuentes.de(" egsa ").proveedor()).isEqualTo("EGSA");
        assertThat(fuentes.de("AUTOPARTES DEL SUR").recibeArchivo()).isFalse();
    }

    @Test
    void proveedorDesconocido() {
        assertThatThrownBy(() -> fuentes.de("OTRO")).isInstanceOf(BusinessException.class)
                .hasMessage("No hay actualización automática para el proveedor OTRO");
    }

    @Test
    void todas_sonAdsYEgsa() {
        assertThat(fuentes.todas()).extracting(FuenteLista::nombre).containsExactly("ADS", "EGSA");
        assertThat(fuentes.de("EGSA").recibeArchivo()).isTrue();
        assertThat(fuentes.de("EGSA").maxAltas()).isEqualTo(300);
        assertThat(fuentes.de("ADS").maxAltas()).isZero();
        assertThat(fuentes.de("EGSA").diasSinActualizar()).isEqualTo(2);
    }

    @Test
    void etiquetasDeLosLotes() {
        FuenteLista f = fuentes.de("ADS");
        assertThat(f.etiqueta(OrigenSincronizacion.AUTOMATICA)).isEqualTo("Actualización automática de ADS");
        assertThat(f.etiqueta(OrigenSincronizacion.MANUAL)).isEqualTo("Actualización de ADS (botón)");
        assertThat(f.etiqueta(OrigenSincronizacion.RECEPCION)).isEqualTo("Lista recibida de ADS");
        assertThat(f.fuenteLote(OrigenSincronizacion.AUTOMATICA)).isEqualTo("API_SYNC");
        assertThat(f.fuenteLote(OrigenSincronizacion.MANUAL)).isEqualTo("API_SYNC");
        assertThat(f.fuenteLote(OrigenSincronizacion.RECEPCION)).isEqualTo("API_ENVIO");
    }

    @Test
    void laClaveNoSeMuestraEnNingunLog() {
        assertThat(egsa.toString()).doesNotContain("clave-de-prueba").contains("habilitada=true");
        assertThat(egsa.habilitada()).isTrue();
        assertThat(new EgsaRecepcionProperties(null, "EGSA", "c", "p", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1).habilitada()).isFalse();
        assertThat(new EgsaRecepcionProperties("  ", "EGSA", "c", "p", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1).habilitada()).isFalse();
    }

    // --- Archivo retenido (JDBC) ---

    @Test
    void listasRetenidas_guardaConUpsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        byte[] archivo = {1, 2, 3};

        new ListasRetenidas(jdbc).guardar("EGSA", archivo, "lista.xlsx");

        verify(jdbc).update(anyString(), eq("EGSA"), eq(archivo), eq("lista.xlsx"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listasRetenidas_lee_ySiNoHayDevuelveVacio() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        byte[] archivo = {9};
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("EGSA"))).thenReturn(List.of(archivo));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("OTRO"))).thenReturn(List.of());
        ListasRetenidas retenidas = new ListasRetenidas(jdbc);

        assertThat(retenidas.leer("EGSA")).contains(archivo);
        assertThat(retenidas.leer("OTRO")).isEqualTo(Optional.empty());
    }

    @Test
    void listasRetenidas_hayYBorrar() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("EGSA"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ADS"))).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("X"))).thenReturn(null);
        ListasRetenidas retenidas = new ListasRetenidas(jdbc);

        assertThat(retenidas.hay("EGSA")).isTrue();
        assertThat(retenidas.hay("ADS")).isFalse();
        assertThat(retenidas.hay("X")).isFalse();
        retenidas.borrar("EGSA");
        verify(jdbc).update(anyString(), eq("EGSA"));
    }
}
