package com.partvision.location.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.dto.UbicacionResponse;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UbicacionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UbicacionControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private UbicacionService ubicacionService;
    @MockBean
    private JwtService jwtService;

    @Test
    void create_devuelve201() throws Exception {
        when(ubicacionService.create(any()))
                .thenReturn(new UbicacionResponse(1L, TipoUbicacion.DEPOSITO, "A", "A", true, null));

        mvc.perform(post("/api/v1/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"DEPOSITO\",\"codigo\":\"A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.path").value("A"));
    }

    @Test
    void create_sinTipo_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_jerarquiaInvalida_devuelve422() throws Exception {
        when(ubicacionService.create(any())).thenThrow(new BusinessException("jerarquia invalida"));

        mvc.perform(post("/api/v1/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"DEPOSITO\",\"codigo\":\"B\",\"parentId\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void findById_devuelve200() throws Exception {
        when(ubicacionService.findById(1L))
                .thenReturn(new UbicacionResponse(1L, TipoUbicacion.DEPOSITO, "A", "A", true, null));

        mvc.perform(get("/api/v1/ubicaciones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("A"));
    }

    @Test
    void findById_inexistente_devuelve404() throws Exception {
        when(ubicacionService.findById(99L)).thenThrow(new ResourceNotFoundException("Ubicacion", 99L));

        mvc.perform(get("/api/v1/ubicaciones/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findRaices_devuelve200() throws Exception {
        when(ubicacionService.findRaices())
                .thenReturn(List.of(new UbicacionResponse(1L, TipoUbicacion.DEPOSITO, "A", "A", true, null)));

        mvc.perform(get("/api/v1/ubicaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("A"));
    }

    @Test
    void findHijos_devuelve200() throws Exception {
        when(ubicacionService.findHijos(1L))
                .thenReturn(List.of(new UbicacionResponse(2L, TipoUbicacion.PASILLO, "1", "A/1", true, 1L)));

        mvc.perform(get("/api/v1/ubicaciones/1/hijos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("A/1"));
    }
}
