package com.partvision.catalog.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoListItemResponse;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ProductoService productoService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private ProductoResponse sampleProducto() {
        return new ProductoResponse(1L, "ABC-123", 5L, "Bosch", 7L, "Filtros",
                "Filtro de aceite", ProductoEstado.ACTIVO, Map.of("origen", "Argentina"), List.of(), "Autopartes SA");
    }

    @Test
    void create_devuelve201() throws Exception {
        when(productoService.create(any())).thenReturn(sampleProducto());

        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Filtro de aceite\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.marcaNombre").value("Bosch"));
    }

    @Test
    void create_descripcionVacia_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_codigoDuplicado_devuelve409() throws Exception {
        when(productoService.create(any())).thenThrow(new DuplicateResourceException("El codigo ya esta registrado"));

        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Filtro\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void agregarCodigo_devuelve201() throws Exception {
        when(productoService.agregarCodigo(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(sampleProducto());

        mvc.perform(post("/api/v1/productos/1/codigos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"7791234567890\",\"tipo\":\"BARRA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void agregarCodigo_codigoVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/productos/1/codigos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agregarCodigo_duplicado_devuelve409() throws Exception {
        when(productoService.agregarCodigo(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new DuplicateResourceException("El codigo ya esta registrado"));

        mvc.perform(post("/api/v1/productos/1/codigos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"7791234567890\",\"tipo\":\"BARRA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void findById_devuelve200() throws Exception {
        when(productoService.findById(1L)).thenReturn(sampleProducto());

        mvc.perform(get("/api/v1/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Filtro de aceite"));
    }

    @Test
    void findById_inexistente_devuelve404() throws Exception {
        when(productoService.findById(99L)).thenThrow(new ResourceNotFoundException("Producto", 99L));

        mvc.perform(get("/api/v1/productos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findAll_devuelve200() throws Exception {
        var item = new ProductoListItemResponse(1L, "ABC-123", "Filtro de aceite",
                ProductoEstado.ACTIVO, "Bosch", "Filtros");
        when(productoService.findAll(any())).thenReturn(new PageImpl<>(List.of(item)));

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].descripcion").value("Filtro de aceite"));
    }

    @Test
    void buscarPorCodigo_existente_devuelve200() throws Exception {
        when(productoService.buscarPorCodigo("7791234567890")).thenReturn(sampleProducto());

        mvc.perform(get("/api/v1/productos/buscar").param("codigo", "7791234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("ABC-123"));
    }

    @Test
    void buscarPorTexto_devuelve200() throws Exception {
        var item = new ProductoListItemResponse(1L, "VW-1", "Aros Volkswagen",
                ProductoEstado.ACTIVO, "Power Engine", "Aros");
        when(productoService.buscarPorTexto(any(), any())).thenReturn(new PageImpl<>(List.of(item)));

        mvc.perform(get("/api/v1/productos/buscar-texto").param("q", "volks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].descripcion").value("Aros Volkswagen"));
    }

    @Test
    void buscarPorCodigo_inexistente_devuelve404() throws Exception {
        when(productoService.buscarPorCodigo("nope"))
                .thenThrow(new ResourceNotFoundException("No existe un producto con el codigo: nope"));

        mvc.perform(get("/api/v1/productos/buscar").param("codigo", "nope"))
                .andExpect(status().isNotFound());
    }
}
