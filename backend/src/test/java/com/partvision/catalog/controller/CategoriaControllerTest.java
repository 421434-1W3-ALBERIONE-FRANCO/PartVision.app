package com.partvision.catalog.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.catalog.dto.CategoriaResponse;
import com.partvision.catalog.service.CategoriaService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CategoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CategoriaControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CategoriaService categoriaService;
    @MockBean
    private JwtService jwtService;

    @Test
    void create_devuelve201() throws Exception {
        when(categoriaService.create(any())).thenReturn(new CategoriaResponse(1L, "Filtros", null, null));

        mvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Filtros\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Filtros"));
    }

    @Test
    void create_nombreVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_devuelve200() throws Exception {
        when(categoriaService.findAll()).thenReturn(List.of(new CategoriaResponse(1L, "Filtros", null, null)));

        mvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Filtros"));
    }

    @Test
    void findById_existente_devuelve200() throws Exception {
        when(categoriaService.findById(1L)).thenReturn(new CategoriaResponse(1L, "Filtros", null, null));

        mvc.perform(get("/api/v1/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Filtros"));
    }

    @Test
    void findById_inexistente_devuelve404() throws Exception {
        when(categoriaService.findById(99L)).thenThrow(new ResourceNotFoundException("Categoria", 99L));

        mvc.perform(get("/api/v1/categorias/99"))
                .andExpect(status().isNotFound());
    }
}
