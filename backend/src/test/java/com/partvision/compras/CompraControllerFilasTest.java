package com.partvision.compras;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.compras.dto.FilaSheetRequest;
import com.partvision.compras.dto.RecepcionFilasResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El cuerpo que manda Power Automate al leer la tabla: {@code value} con una fila por
 * producto, los nombres de columna de la planilla y los campos propios de Power Automate.
 */
@WebMvcTest(controllers = CompraController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "partvision.compras.api-key=clave-secreta")
class CompraControllerFilasTest {

    private static final String URL = "/api/v1/compras/recepcion/filas";

    /** Tal como sale de "Enumerar las filas de una tabla", con la fecha como numero de serie. */
    private static final String CUERPO_POWER_AUTOMATE = """
            {"@odata.context": "https://excelonline/$metadata#items",
             "value": [
               {"@odata.etag": "", "ItemInternalId": "a1", "Factura": "900004110", "F. Factura": "46258",
                "Codigo": "808449(05)", "Cantidad": "1",
                "Descripcion": "AROS RECTIFICACION VOLKSWAGEN - GOLF", "Estatus stock": "EN TRÁNSITO"},
               {"@odata.etag": "", "ItemInternalId": "a2", "Factura": "900000482", "F. Factura": "46259",
                "Codigo": "", "Cantidad": 2, "Descripcion": "bie0381 biela om651",
                "Estatus stock": "INGRESADA", "Proveedor": "ADS"}
             ]}
            """;

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CompraService compraService;
    @MockBean
    private RecepcionFilasService recepcionFilasService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private static RecepcionFilasResponse respuesta() {
        return RecepcionFilasResponse.de(2, List.of(
                new RecepcionFilasResponse.ResultadoFactura("900004110", "CREADA", "EN_TRANSITO", 1, 1, "registrada")),
                List.of());
    }

    @Test
    void cuerpoDePowerAutomate_seLeeConLosNombresDeLaPlanilla() throws Exception {
        when(recepcionFilasService.recibir(any())).thenReturn(respuesta());

        mvc.perform(post(URL)
                        .header("X-API-Key", "clave-secreta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_POWER_AUTOMATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadas").value(1))
                .andExpect(jsonPath("$.resultados[0].factura").value("900004110"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FilaSheetRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(recepcionFilasService).recibir(captor.capture());
        List<FilaSheetRequest> filas = captor.getValue();
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).factura()).isEqualTo("900004110");
        assertThat(filas.get(0).fechaFactura()).isEqualTo("46258");
        assertThat(filas.get(0).codigo()).isEqualTo("808449(05)");
        assertThat(filas.get(0).estatus()).isEqualTo("EN TRÁNSITO");
        // Una cantidad numerica tambien llega como texto.
        assertThat(filas.get(1).cantidad()).isEqualTo("2");
        assertThat(filas.get(1).proveedor()).isEqualTo("ADS");
    }

    @Test
    void conLosNombresDeCampoPropios_tambienFunciona() throws Exception {
        when(recepcionFilasService.recibir(any())).thenReturn(respuesta());

        mvc.perform(post(URL)
                        .header("X-API-Key", "clave-secreta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filas": [{"factura": "1", "fechaFactura": "24/08/2026",
                                            "codigo": "X", "cantidad": "1", "estatus": "EN TRÁNSITO"}]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void sinApiKey_devuelve401YNoProcesa() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_POWER_AUTOMATE))
                .andExpect(status().isUnauthorized());

        verify(recepcionFilasService, never()).recibir(any());
    }

    @Test
    void sinFilas_devuelve400() throws Exception {
        mvc.perform(post(URL)
                        .header("X-API-Key", "clave-secreta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
