package com.partvision.catalog.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.service.MarcaService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarcaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MarcaControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private MarcaService marcaService;
    @MockBean
    private JwtService jwtService;

    @Test
    void create_devuelve201() throws Exception {
        when(marcaService.create(any())).thenReturn(new MarcaResponse(1L, "Bosch"));

        mvc.perform(post("/api/v1/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bosch\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Bosch"));
    }

    @Test
    void create_nombreVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicada_devuelve409() throws Exception {
        when(marcaService.create(any())).thenThrow(new DuplicateResourceException("La marca ya existe"));

        mvc.perform(post("/api/v1/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bosch\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void findAll_devuelve200() throws Exception {
        when(marcaService.findAll()).thenReturn(List.of(new MarcaResponse(1L, "Bosch")));

        mvc.perform(get("/api/v1/marcas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Bosch"));
    }

    @Test
    void findById_existente_devuelve200() throws Exception {
        when(marcaService.findById(1L)).thenReturn(new MarcaResponse(1L, "Bosch"));

        mvc.perform(get("/api/v1/marcas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bosch"));
    }

    @Test
    void findById_inexistente_devuelve404() throws Exception {
        when(marcaService.findById(99L)).thenThrow(new ResourceNotFoundException("Marca", 99L));

        mvc.perform(get("/api/v1/marcas/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_devuelve200() throws Exception {
        when(marcaService.update(eq(1L), any())).thenReturn(new MarcaResponse(1L, "Bosch"));

        mvc.perform(put("/api/v1/marcas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bosch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bosch"));
    }

    @Test
    void update_nombreVacio_devuelve400() throws Exception {
        mvc.perform(put("/api/v1/marcas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_devuelve204() throws Exception {
        doNothing().when(marcaService).delete(1L);

        mvc.perform(delete("/api/v1/marcas/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_conProductos_devuelve422() throws Exception {
        doThrow(new BusinessException("tiene productos asociados")).when(marcaService).delete(1L);

        mvc.perform(delete("/api/v1/marcas/1"))
                .andExpect(status().isUnprocessableEntity());
    }
}
