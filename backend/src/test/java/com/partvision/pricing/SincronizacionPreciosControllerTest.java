package com.partvision.pricing;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.pricing.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SincronizacionPreciosController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SincronizacionPreciosControllerTest {

    private static final String URL = "/api/v1/precios/sincronizacion";

    @Autowired private MockMvc mvc;
    @MockBean private SincronizacionPreciosService service;
    @MockBean private JwtService jwtService;
    @MockBean private TokenRevocationService revocationService;

    private static SincronizacionResponse corrida(String resultado) {
        return new SincronizacionResponse(1, "Autopartes del Sur", "MANUAL", false, LocalDateTime.now(), null,
                resultado, null, List.of(), null, 0, 0, 0, 0, 0, 0, null);
    }

    @Test
    void estado() throws Exception {
        when(service.estado()).thenReturn(new SincronizacionEstadoResponse(true, "Autopartes del Sur", false,
                corrida("ACTUALIZADA"), LocalDateTime.now(), 3, AlertaPreciosResponse.aviso("revisar"), List.of()));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true))
                .andExpect(jsonPath("$.pendientesRevision").value(3))
                .andExpect(jsonPath("$.alerta.nivel").value("AVISO"))
                .andExpect(jsonPath("$.ultima.resultado").value("ACTUALIZADA"));
    }

    @Test
    void actualizarAhora_devuelve202() throws Exception {
        when(service.iniciarManual(false)).thenReturn(corrida("EN_CURSO"));

        mvc.perform(post(URL))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resultado").value("EN_CURSO"));
    }

    @Test
    void aplicarIgual_pasaElForzar() throws Exception {
        when(service.iniciarManual(true)).thenReturn(corrida("EN_CURSO"));

        mvc.perform(post(URL).param("forzar", "true")).andExpect(status().isAccepted());

        verify(service).iniciarManual(true);
    }

    @Test
    void actualizarAhora_sinConfigurar_devuelve422ConElMotivo() throws Exception {
        when(service.iniciarManual(false)).thenThrow(new BusinessException("no está configurada"));

        mvc.perform(post(URL))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("no está configurada"));
    }

    @Test
    void alerta_conYSinAviso() throws Exception {
        when(service.alerta()).thenReturn(Optional.of(AlertaPreciosResponse.error("ADS caido")));
        mvc.perform(get(URL + "/alerta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ERROR"))
                .andExpect(jsonPath("$.mensaje").value("ADS caido"));

        when(service.alerta()).thenReturn(Optional.empty());
        mvc.perform(get(URL + "/alerta")).andExpect(status().isNoContent());
    }

    @Test
    void revisiones() throws Exception {
        when(service.revisionesPendientes()).thenReturn(List.of(new PrecioRevisionResponse(10, 7, "A1", "Junta",
                new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("400.00"))));

        mvc.perform(get(URL + "/revisiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("A1"))
                .andExpect(jsonPath("$[0].variacionPct").value(400.00));
    }

    @Test
    void aplicarYDescartar_conLosIdsElegidos() throws Exception {
        when(service.aplicarRevisiones(List.of(10L, 11L))).thenReturn(new RevisionPreciosResponse(2, 99L, "ok"));
        when(service.descartarRevisiones(List.of(12L))).thenReturn(new RevisionPreciosResponse(1, null, "listo"));

        mvc.perform(post(URL + "/revisiones/aplicar").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(99));
        mvc.perform(post(URL + "/revisiones/descartar").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[12]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("listo"));
    }

    @Test
    void aplicarYDescartar_sinCuerpo_losRechazaElServicio() throws Exception {
        when(service.aplicarRevisiones(null)).thenThrow(new BusinessException("Elegí al menos un precio."));
        when(service.descartarRevisiones(null)).thenThrow(new BusinessException("Elegí al menos un precio."));

        mvc.perform(post(URL + "/revisiones/aplicar")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(URL + "/revisiones/descartar")).andExpect(status().isUnprocessableEntity());
    }
}
