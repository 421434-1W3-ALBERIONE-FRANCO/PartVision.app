package com.partvision.compras;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.dto.CompraResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "partvision.compras.api-key=clave-secreta")
class CompraControllerTest {

    private static final String BODY = """
            {"factura":"A-0001-00012345","fechaFactura":"2026-09-11","proveedor":"EGSA",
             "estatus":"PENDIENTE","lineas":[{"codigo":"0082-20-00","cantidad":4}]}
            """;

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CompraService compraService;
    @MockBean
    private RecepcionFilasService recepcionFilasService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private static CompraResponse respuesta() {
        return new CompraResponse(1L, "A-0001-00012345", LocalDate.of(2026, 9, 11), "EGSA",
                "PENDIENTE", "EN_TRANSITO", null, null, 1, 4, 0, Instant.now(), List.of());
    }

    @Test
    void recepcion_conApiKeyCorrecta_devuelve201() throws Exception {
        when(compraService.registrarRecepcion(any())).thenReturn(respuesta());

        mvc.perform(post("/api/v1/compras/recepcion")
                        .header("X-API-Key", "clave-secreta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroFactura").value("A-0001-00012345"));
    }

    @Test
    void recepcion_conApiKeyIncorrecta_devuelve401() throws Exception {
        mvc.perform(post("/api/v1/compras/recepcion")
                        .header("X-API-Key", "otra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recepcion_sinApiKey_devuelve401() throws Exception {
        mvc.perform(post("/api/v1/compras/recepcion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarEstado_aTransito_pasaElEstadoAlServicio() throws Exception {
        when(compraService.cambiarEstado(any(), any())).thenReturn(respuesta());

        mvc.perform(patch("/api/v1/compras/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_TRANSITO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPlanilla").value("EN_TRANSITO"));

        verify(compraService).cambiarEstado(7L, CompraEstado.EN_TRANSITO);
    }

    @Test
    void cambiarEstado_sinEstado_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/compras/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarEstado_conUnEstadoQueNoExiste_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/compras/7/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
    }
}
