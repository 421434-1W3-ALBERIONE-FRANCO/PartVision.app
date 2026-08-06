package com.partvision.imports.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.imports.dto.ImportResultResponse;
import com.partvision.imports.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ImportService importService;
    @MockBean
    private JwtService jwtService;

    @Test
    void importarProductos_devuelve200ConResumen() throws Exception {
        when(importService.importarCsv(any()))
                .thenReturn(new ImportResultResponse(3, 1, 1,
                        List.of(new ImportResultResponse.FilaError(3, "La descripcion es obligatoria"))));

        MockMultipartFile archivo = new MockMultipartFile("archivo", "productos.csv", "text/csv",
                "descripcion\nFiltro".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/importaciones/productos").file(archivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFilas").value(3))
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.omitidos").value(1))
                .andExpect(jsonPath("$.errores[0].fila").value(3));
    }
}
