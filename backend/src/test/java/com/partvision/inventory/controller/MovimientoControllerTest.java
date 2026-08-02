package com.partvision.inventory.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MovimientoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MovimientoControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private StockService stockService;
    @MockBean
    private JwtService jwtService;

    @Test
    void historial_devuelve200() throws Exception {
        MovimientoResponse m = new MovimientoResponse(100L, 1L, TipoMovimiento.ENTRADA, 5,
                null, 10L, 7L, "compra", null, Instant.now());
        when(stockService.listarMovimientos(eq(1L), any())).thenReturn(new PageImpl<>(List.of(m)));

        mvc.perform(get("/api/v1/movimientos").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tipo").value("ENTRADA"));
    }
}
