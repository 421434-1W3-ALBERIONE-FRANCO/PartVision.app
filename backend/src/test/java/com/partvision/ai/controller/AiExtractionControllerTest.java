package com.partvision.ai.controller;

import com.partvision.ai.domain.EstadoExtraccion;
import com.partvision.ai.dto.AccionSugerida;
import com.partvision.ai.dto.AiExtractionResponse;
import com.partvision.ai.dto.ConfirmacionResponse;
import com.partvision.ai.dto.SugerenciaAccionResponse;
import com.partvision.ai.service.AiExtractionService;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiExtractionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AiExtractionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private AiExtractionService aiExtractionService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private AiExtractionResponse pendiente() {
        return new AiExtractionResponse(1L, "k.jpg", "stub-vision", EstadoExtraccion.PENDIENTE, Map.of(), null);
    }

    @Test
    void extraer_devuelve201() throws Exception {
        when(aiExtractionService.extraer(any())).thenReturn(pendiente());
        MockMultipartFile img = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[]{1, 2});

        mvc.perform(multipart("/api/v1/extracciones").file(img))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void confirmar_devuelve201() throws Exception {
        ProductoResponse prod = new ProductoResponse(50L, null, null, null, null, null, "Filtro",
                ProductoEstado.ACTIVO, Map.of(), List.of(), null, null, null, null, null);
        when(aiExtractionService.confirmar(eq(1L), any()))
                .thenReturn(new ConfirmacionResponse(1L, prod, null));

        mvc.perform(post("/api/v1/extracciones/1/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"producto\":{\"descripcion\":\"Filtro\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.producto.id").value(50));
    }

    @Test
    void confirmar_bodySinProducto_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/extracciones/1/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sugerencia_devuelve200() throws Exception {
        when(aiExtractionService.analizar(1L)).thenReturn(new SugerenciaAccionResponse(
                AccionSugerida.AGREGAR_CODIGO, 50L, "Filtro", "779100", "ya existe pero le falta el codigo",
                java.util.List.of()));

        mvc.perform(get("/api/v1/extracciones/1/sugerencia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("AGREGAR_CODIGO"))
                .andExpect(jsonPath("$.productoExistenteId").value(50));
    }

    @Test
    void asociarCodigo_devuelve200() throws Exception {
        when(aiExtractionService.asociarCodigo(eq(1L), eq(50L)))
                .thenReturn(new AiExtractionResponse(1L, "k.jpg", "stub-vision", EstadoExtraccion.CONFIRMADA, Map.of(), 50L));

        mvc.perform(post("/api/v1/extracciones/1/asociar-codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.productoId").value(50));
    }

    @Test
    void asociarCodigo_sinProductoId_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/extracciones/1/asociar-codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descartar_devuelve200() throws Exception {
        when(aiExtractionService.descartar(1L))
                .thenReturn(new AiExtractionResponse(1L, "k.jpg", "stub-vision", EstadoExtraccion.DESCARTADA, Map.of(), null));

        mvc.perform(post("/api/v1/extracciones/1/descartar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESCARTADA"));
    }

    @Test
    void confirmar_extraccionYaProcesada_devuelve422() throws Exception {
        when(aiExtractionService.confirmar(eq(1L), any())).thenThrow(new BusinessException("ya procesada"));

        mvc.perform(post("/api/v1/extracciones/1/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"producto\":{\"descripcion\":\"Filtro\"}}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void findById_devuelve200() throws Exception {
        when(aiExtractionService.findById(1L)).thenReturn(pendiente());

        mvc.perform(get("/api/v1/extracciones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(aiExtractionService.listarPorEstado(eq(EstadoExtraccion.PENDIENTE), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendiente())));

        mvc.perform(get("/api/v1/extracciones").param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }
}
