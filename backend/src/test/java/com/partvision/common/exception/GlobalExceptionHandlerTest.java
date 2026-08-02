package com.partvision.common.exception;

import com.partvision.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestExceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;
    // El filtro JWT (bean Filter) se incluye en el slice; necesita este colaborador.
    @MockBean
    private JwtService jwtService;

    @Test
    void resourceNotFound_devuelve404() throws Exception {
        mvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Producto no encontrado: 1"))
                .andExpect(jsonPath("$.path").value("/test-errors/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void duplicate_devuelve409() throws Exception {
        mvc.perform(get("/test-errors/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("SKU duplicado"));
    }

    @Test
    void business_devuelve422() throws Exception {
        mvc.perform(get("/test-errors/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Stock insuficiente"));
    }

    @Test
    void excepcionNoControlada_devuelve500() throws Exception {
        mvc.perform(get("/test-errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }

    @Test
    void bodyInvalido_devuelve400ConDetalles() throws Exception {
        mvc.perform(post("/test-errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Datos de entrada invalidos"))
                .andExpect(jsonPath("$.details[0].field").value("nombre"))
                .andExpect(jsonPath("$.details[0].message").value("no debe estar vacio"));
    }
}
