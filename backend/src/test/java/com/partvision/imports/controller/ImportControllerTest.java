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
import static org.mockito.Mockito.when;
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
}
