package com.partvision.imports.controller;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.security.JwtAuthenticationFilter;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.RestAuthenticationEntryPoint;
import com.partvision.auth.security.SecurityConfig;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.imports.service.CatalogoMantenimientoService;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ImportJobRegistry;
import com.partvision.imports.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autentica por header {@code Bearer} (auth de herramientas/API): el JWT valido aporta el
 * rol y la request queda exenta de CSRF. El CSRF del panel web se cubre en
 * {@code SecurityIntegrationTest}.
 */
@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class ImportControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private ImportService importService;
    @MockBean
    private ImportJobRegistry jobRegistry;
    @MockBean
    private CatalogoMantenimientoService catalogoMantenimientoService;
    @MockBean
    private TokenRevocationService revocationService;

    private String bearer(String rol) {
        Usuario u = Usuario.builder()
                .id(1L).username("u")
                .roles(Set.of(Rol.builder().nombre(rol).build()))
                .build();
        return "Bearer " + jwtService.generateToken(u);
    }

    private MockMultipartFile csv() {
        return new MockMultipartFile("archivo", "productos.csv", "text/csv",
                "descripcion\nFiltro".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importarProductos_comoAdmin_devuelve202ConJobId() throws Exception {
        when(jobRegistry.crear(anyInt())).thenReturn(new ImportJob("job-123", 1));

        mvc.perform(multipart("/api/v1/importaciones/productos").file(csv())
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
    }

    @Test
    void importarProductos_comoOperario_devuelve403() throws Exception {
        mvc.perform(multipart("/api/v1/importaciones/productos").file(csv())
                        .header("Authorization", bearer("OPERARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void importarProductos_archivoVacio_devuelve422() throws Exception {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "vacio.csv", "text/csv", new byte[0]);

        mvc.perform(multipart("/api/v1/importaciones/productos").file(vacio)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void estado_jobExistente_devuelve200() throws Exception {
        ImportJob job = new ImportJob("job-456", 10);
        job.marcarProcesada();
        job.marcarImportada();
        when(jobRegistry.get("job-456")).thenReturn(job);

        mvc.perform(get("/api/v1/importaciones/productos/job-456")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-456"))
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.procesados").value(1))
                .andExpect(jsonPath("$.importados").value(1));
    }

    @Test
    void estado_jobInexistente_devuelve404() throws Exception {
        when(jobRegistry.get("no-existe")).thenReturn(null);

        mvc.perform(get("/api/v1/importaciones/productos/no-existe")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void estado_comoOperario_devuelve403() throws Exception {
        mvc.perform(get("/api/v1/importaciones/productos/job-456")
                        .header("Authorization", bearer("OPERARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void vaciarCatalogo_comoAdmin_devuelve200() throws Exception {
        when(catalogoMantenimientoService.vaciarCatalogo()).thenReturn(42);

        mvc.perform(delete("/api/v1/importaciones/catalogo")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosBorrados").value(42));

        verify(catalogoMantenimientoService).vaciarCatalogo();
    }

    @Test
    void vaciarCatalogo_comoOperario_devuelve403() throws Exception {
        mvc.perform(delete("/api/v1/importaciones/catalogo")
                        .header("Authorization", bearer("OPERARIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/importaciones/productos/job-1"))
                .andExpect(status().isUnauthorized());
    }
}
