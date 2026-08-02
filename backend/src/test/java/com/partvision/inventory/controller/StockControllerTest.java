package com.partvision.inventory.controller;

import com.partvision.auth.security.JwtService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.dto.StockResumenResponse;
import com.partvision.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StockControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private StockService stockService;
    @MockBean
    private JwtService jwtService;

    private MovimientoResponse movimiento(TipoMovimiento tipo) {
        return new MovimientoResponse(100L, 1L, tipo, 5, null, 10L, 7L, "motivo", null, Instant.now());
    }

    @Test
    void entrada_devuelve201() throws Exception {
        when(stockService.registrarEntrada(any())).thenReturn(movimiento(TipoMovimiento.ENTRADA));

        mvc.perform(post("/api/v1/stock/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionId\":10,\"cantidad\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ENTRADA"));
    }

    @Test
    void entrada_cantidadInvalida_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/stock/entradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionId\":10,\"cantidad\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salida_devuelve201() throws Exception {
        when(stockService.registrarSalida(any())).thenReturn(movimiento(TipoMovimiento.SALIDA));

        mvc.perform(post("/api/v1/stock/salidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionId\":10,\"cantidad\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("SALIDA"));
    }

    @Test
    void salida_stockInsuficiente_devuelve422() throws Exception {
        when(stockService.registrarSalida(any())).thenThrow(new BusinessException("Stock insuficiente"));

        mvc.perform(post("/api/v1/stock/salidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionId\":10,\"cantidad\":5}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ajuste_devuelve201() throws Exception {
        when(stockService.ajustar(any())).thenReturn(movimiento(TipoMovimiento.AJUSTE_POSITIVO));

        mvc.perform(post("/api/v1/stock/ajustes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionId\":10,\"tipo\":\"AJUSTE_POSITIVO\",\"cantidad\":5,\"motivo\":\"recuento\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE_POSITIVO"));
    }

    @Test
    void transferencia_devuelve201() throws Exception {
        when(stockService.transferir(any())).thenReturn(movimiento(TipoMovimiento.TRANSFERENCIA));

        mvc.perform(post("/api/v1/stock/transferencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"ubicacionOrigenId\":1,\"ubicacionDestinoId\":2,\"cantidad\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA"));
    }

    @Test
    void resumen_devuelve200() throws Exception {
        when(stockService.getResumen(1L)).thenReturn(new StockResumenResponse(1L, 35,
                List.of(new StockResumenResponse.StockLinea(10L, "A/1", 35))));

        mvc.perform(get("/api/v1/stock").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(35))
                .andExpect(jsonPath("$.ubicaciones[0].ubicacionPath").value("A/1"));
    }
}
