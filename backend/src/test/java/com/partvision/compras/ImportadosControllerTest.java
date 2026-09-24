package com.partvision.compras;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.compras.dto.AltaImportadoRequest;
import com.partvision.compras.dto.ImportadoPendienteResponse;
import com.partvision.compras.dto.ImportadoResueltoResponse;
import com.partvision.compras.dto.VincularImportadoRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportadosController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ImportadosControllerTest {

    private static final String URL = "/api/v1/compras/importados";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ImportadosService importadosService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private static ImportadoResueltoResponse resuelto(boolean stock) {
        return new ImportadoResueltoResponse(40L, 900L, "IMP-00001", "BIELA OM651", stock,
                stock ? "A-01" : null, "Dado de alta como IMP-00001");
    }

    @Test
    void pendientes_devuelveLaPagina() throws Exception {
        when(importadosService.listarPendientes(any())).thenReturn(new PageImpl<>(List.of(
                new ImportadoPendienteResponse(40L, 3L, "900000482", LocalDate.of(2026, 8, 25), "EGSA",
                        "EN_TRANSITO", "bie0381 biela om651", 2,
                        new ImportadoPendienteResponse.Sugerencia(
                                901L, "BIE0381", "BIELA OM651", "EGSA", true)))));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lineaId").value(40))
                .andExpect(jsonPath("$.content[0].descripcion").value("bie0381 biela om651"))
                .andExpect(jsonPath("$.content[0].sugerencia.sku").value("BIE0381"))
                .andExpect(jsonPath("$.content[0].sugerencia.mismoProveedor").value(true));
    }

    @Test
    void skuSugerido_devuelveElProximoLibre() throws Exception {
        when(importadosService.proponerSku()).thenReturn("IMP-00001");

        mvc.perform(get(URL + "/sku-sugerido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("IMP-00001"));
    }

    @Test
    void alta_devuelve201() throws Exception {
        when(importadosService.darDeAlta(eq(40L), any(AltaImportadoRequest.class))).thenReturn(resuelto(true));

        mvc.perform(post(URL + "/40/alta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"IMP-00001\",\"descripcion\":\"BIELA OM651\",\"ubicacionId\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("IMP-00001"))
                .andExpect(jsonPath("$.stockCargado").value(true));
    }

    @Test
    void alta_sinSku_devuelve400() throws Exception {
        mvc.perform(post(URL + "/40/alta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"BIELA OM651\"}"))
                .andExpect(status().isBadRequest());

        verify(importadosService, never()).darDeAlta(any(), any());
    }

    @Test
    void vincular_devuelve200() throws Exception {
        when(importadosService.vincular(eq(40L), any(VincularImportadoRequest.class))).thenReturn(resuelto(false));

        mvc.perform(post(URL + "/40/vincular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":900}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value(900));
    }

    @Test
    void vincular_sinProducto_devuelve400() throws Exception {
        mvc.perform(post(URL + "/40/vincular")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
