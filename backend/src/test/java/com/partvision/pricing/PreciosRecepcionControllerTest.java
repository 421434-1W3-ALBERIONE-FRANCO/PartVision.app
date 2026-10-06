package com.partvision.pricing;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.pricing.dto.SincronizacionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PreciosRecepcionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "partvision.precios.egsa.api-key=clave-de-prueba",
        "partvision.precios.egsa.max-bytes=64"
})
class PreciosRecepcionControllerTest {

    private static final String URL = "/api/v1/precios/recepcion";
    private static final byte[] EXCEL = "PK\u0003\u0004 un xlsx de mentira".getBytes();

    @Autowired private MockMvc mvc;
    @MockBean private RecepcionListaService service;
    @MockBean private JwtService jwtService;
    @MockBean private TokenRevocationService revocationService;

    private static SincronizacionResponse constancia(String resultado) {
        return new SincronizacionResponse(7, "EGSA", "RECEPCION", false, LocalDateTime.now(), null,
                resultado, null, List.of(), null, 0, 0, 0, 0, 0, 0, null);
    }

    @Test
    void recibe_elArchivoCrudo_ylodevuelve202ConLaConstancia() throws Exception {
        when(service.recibir(any(), eq("EGSA_ListaPrecios_05-10-2026.xlsx"))).thenReturn(constancia("EN_CURSO"));

        mvc.perform(post(URL + "/egsa")
                        .header("X-API-Key", "clave-de-prueba")
                        .header("X-Filename", "EGSA_ListaPrecios_05-10-2026.xlsx")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(EXCEL))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.resultado").value("EN_CURSO"));

        verify(service).recibir(EXCEL, "EGSA_ListaPrecios_05-10-2026.xlsx");
    }

    @Test
    void recibe_conCualquierContentType_ytambienSinNombre() throws Exception {
        when(service.recibir(any(), eq(null))).thenReturn(constancia("EN_CURSO"));

        mvc.perform(post(URL + "/egsa")
                        .header("X-API-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(EXCEL))
                .andExpect(status().isAccepted());
    }

    @Test
    void sinClave_devuelve401_ySinLeerElArchivo() throws Exception {
        mvc.perform(post(URL + "/egsa").contentType(MediaType.APPLICATION_OCTET_STREAM).content(EXCEL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void conClaveIncorrecta_devuelve401() throws Exception {
        mvc.perform(post(URL + "/egsa").header("X-API-Key", "otra-clave")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(EXCEL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("API key inválida"));

        verifyNoInteractions(service);
    }

    @Test
    void archivoMasGrandeQueElTope_devuelve413() throws Exception {
        mvc.perform(post(URL + "/egsa").header("X-API-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[200]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("El archivo pesa más de 0 MB"));

        verify(service, never()).recibir(any(), anyString());
    }

    @Test
    void siElServicioDiceQueHayOtraActualizacion_devuelve409() throws Exception {
        when(service.recibir(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Hay otra actualización"));

        mvc.perform(post(URL + "/egsa").header("X-API-Key", "clave-de-prueba")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(EXCEL))
                .andExpect(status().isConflict());
    }

    @Test
    void consultaElResultado_conLaClave() throws Exception {
        when(service.resultado(7)).thenReturn(constancia("ACTUALIZADA"));

        mvc.perform(get(URL + "/7").header("X-API-Key", "clave-de-prueba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("ACTUALIZADA"));
    }

    @Test
    void consultaElResultado_sinClave_devuelve401() throws Exception {
        mvc.perform(get(URL + "/7")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void consultaUnaRecepcionQueNoExiste_devuelve404() throws Exception {
        when(service.resultado(99)).thenThrow(new ResourceNotFoundException("No existe esa recepción"));

        mvc.perform(get(URL + "/99").header("X-API-Key", "clave-de-prueba"))
                .andExpect(status().isNotFound());
    }
}
