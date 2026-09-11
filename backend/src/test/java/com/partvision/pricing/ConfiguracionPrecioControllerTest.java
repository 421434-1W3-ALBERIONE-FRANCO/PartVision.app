package com.partvision.pricing;

import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConfiguracionPrecioController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ConfiguracionPrecioControllerTest {

    private static final String URL = "/api/v1/precios/configuracion";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ConfiguracionPrecioRepository repo;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;

    private static ConfiguracionPrecio config(Long id, String proveedor) {
        ConfiguracionPrecio c = new ConfiguracionPrecio();
        c.setId(id);
        c.setProveedor(proveedor);
        c.setMargen(new BigDecimal("35.00"));
        c.setAjusteLista(new BigDecimal("22.50"));
        c.setActivo(true);
        return c;
    }

    @Test
    void listar_devuelveLasConfiguraciones() throws Exception {
        when(repo.findAll()).thenReturn(List.of(config(1L, "ADS"), config(2L, "EGSA")));

        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].proveedor").value("ADS"))
                .andExpect(jsonPath("$[0].ajusteLista").value(22.50));
    }

    @Test
    void crear_proveedorNuevo_devuelve201() throws Exception {
        when(repo.findByProveedorIgnoreCase("ADS")).thenReturn(Optional.empty());
        when(repo.save(any(ConfiguracionPrecio.class))).thenAnswer(inv -> {
            ConfiguracionPrecio c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedor\":\"ADS\",\"margen\":35,\"ajusteLista\":22.5,\"activo\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.proveedor").value("ADS"));
    }

    /** Sin ajusteLista ni activo, el alta usa los valores por defecto de la entidad. */
    @Test
    void crear_sinOpcionales_usaLosDefaults() throws Exception {
        when(repo.findByProveedorIgnoreCase("EGSA")).thenReturn(Optional.empty());
        when(repo.save(any(ConfiguracionPrecio.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedor\":\"EGSA\",\"margen\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ajusteLista").value(0))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crear_sinProveedor_devuelve400() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"margen\":10}"))
                .andExpect(status().isBadRequest());

        verify(repo, never()).save(any());
    }

    @Test
    void crear_proveedorEnBlanco_devuelve400() throws Exception {
        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedor\":\"   \",\"margen\":10}"))
                .andExpect(status().isBadRequest());

        verify(repo, never()).findByProveedorIgnoreCase(anyString());
    }

    @Test
    void crear_proveedorRepetido_devuelve409() throws Exception {
        when(repo.findByProveedorIgnoreCase("ADS")).thenReturn(Optional.of(config(1L, "ADS")));

        mvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proveedor\":\"ADS\",\"margen\":10}"))
                .andExpect(status().isConflict());

        verify(repo, never()).save(any());
    }

    @Test
    void actualizar_existente_devuelve200ConLosNuevosValores() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(config(1L, "ADS")));
        when(repo.save(any(ConfiguracionPrecio.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"margen\":40,\"ajusteLista\":15,\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margen").value(40))
                .andExpect(jsonPath("$.ajusteLista").value(15))
                .andExpect(jsonPath("$.activo").value(false));
    }

    /** Los opcionales ausentes no pisan lo que ya estaba guardado. */
    @Test
    void actualizar_sinOpcionales_conservaLoGuardado() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(config(1L, "ADS")));
        when(repo.save(any(ConfiguracionPrecio.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"margen\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ajusteLista").value(22.50))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(put(URL + "/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"margen\":40}"))
                .andExpect(status().isNotFound());

        verify(repo, never()).save(any());
    }

    @Test
    void actualizar_margenFaltante_devuelve400() throws Exception {
        mvc.perform(put(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
