package com.partvision.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.pricing.dto.SincronizacionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con el ObjectMapper que arma Spring (el mismo que usan los controllers): las fechas sin zona
 * salen marcadas como UTC, asi el navegador las convierte a la hora de quien mira.
 */
@JsonTest
class FechaUtcSerializerTest {

    @Autowired
    private ObjectMapper mapper;

    /** La corrida que el 14/10 se toco a las 2:44 de Buenos Aires y la pantalla mostraba a las 5:44. */
    @Test
    void fechaSinZona_saleComoUtc() throws Exception {
        String json = mapper.writeValueAsString(Map.of("iniciadaEn", LocalDateTime.of(2026, 10, 14, 5, 44, 34, 894_708_000)));

        assertThat(json).isEqualTo("{\"iniciadaEn\":\"2026-10-14T05:44:34.894708Z\"}");
    }

    @Test
    void horaRedonda_llevaLosSegundos() throws Exception {
        assertThat(mapper.writeValueAsString(LocalDateTime.of(2026, 10, 14, 9, 30)))
                .isEqualTo("\"2026-10-14T09:30:00Z\"");
    }

    @Test
    void sinFecha_sigueSaliendoNull() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("terminadaEn", null);

        assertThat(mapper.writeValueAsString(cuerpo)).isEqualTo("{\"terminadaEn\":null}");
    }

    /** Lo que de verdad recibe la pantalla de Precios. */
    @Test
    void laRespuestaDeLaActualizacion_traeLasFechasEnUtc() throws Exception {
        SincronizacionResponse r = new SincronizacionResponse(35, "Autopartes del Sur", "MANUAL", false,
                LocalDateTime.of(2026, 10, 14, 5, 44, 34), LocalDateTime.of(2026, 10, 14, 5, 44, 52), "SIN_CAMBIOS",
                "La lista de ADS no trae precios distintos a los que ya tenes.", List.of(), null,
                66927, 0, 66896, 31, 48, 0, null);

        String json = mapper.writeValueAsString(r);

        assertThat(json).contains("\"iniciadaEn\":\"2026-10-14T05:44:34Z\"")
                .contains("\"terminadaEn\":\"2026-10-14T05:44:52Z\"");
    }
}
