package com.partvision.compras;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La recepcion es publica (Power Automate no se loguea): sin API key configurada
 * el endpoint tiene que apagarse, no aceptar cualquier request.
 */
@WebMvcTest(controllers = CompraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "partvision.compras.api-key=")
class CompraControllerSinClaveTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CompraService compraService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    @Test
    void recepcion_devuelve503_yNoTocaElServicio() throws Exception {
        mvc.perform(post("/api/v1/compras/recepcion")
                        .header("X-API-Key", "lo-que-sea")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"factura":"A-0001-00012345","fechaFactura":"2026-09-11",
                                 "estatus":"PENDIENTE","lineas":[{"codigo":"X","cantidad":1}]}
                                """))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(compraService);
    }
}
