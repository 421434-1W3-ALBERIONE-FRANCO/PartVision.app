package com.partvision.pricing;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin EGSA_API_KEY el endpoint publico se apaga (falla cerrada), aunque llegue cualquier clave. */
@WebMvcTest(controllers = PreciosRecepcionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PreciosRecepcionSinClaveControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private RecepcionListaService service;
    @MockBean private JwtService jwtService;
    @MockBean private TokenRevocationService revocationService;

    @Test
    void sinClaveConfigurada_elEnvioDevuelve503() throws Exception {
        mvc.perform(post("/api/v1/precios/recepcion/egsa").header("X-API-Key", "cualquiera")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content("PK".getBytes()))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(service);
    }

    @Test
    void sinClaveConfigurada_laConsultaDevuelve503() throws Exception {
        mvc.perform(get("/api/v1/precios/recepcion/1").header("X-API-Key", "cualquiera"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sinClaveConfigurada_unaClaveVaciaNoAbreLaPuerta() throws Exception {
        mvc.perform(post("/api/v1/precios/recepcion/egsa").header("X-API-Key", "")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content("PK".getBytes()))
                .andExpect(status().isServiceUnavailable());
    }
}
