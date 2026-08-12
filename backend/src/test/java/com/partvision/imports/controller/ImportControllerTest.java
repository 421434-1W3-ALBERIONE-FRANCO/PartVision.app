package com.partvision.imports.controller;

import com.partvision.auth.security.JwtAuthenticationFilter;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.RestAuthenticationEntryPoint;
import com.partvision.auth.security.SecurityConfig;
import com.partvision.common.security.AuthenticatedUser;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ImportJobRegistry;
import com.partvision.imports.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class ImportControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private ImportService importService;
    @MockBean
    private ImportJobRegistry jobRegistry;

    private UsernamePasswordAuthenticationToken auth(String rol) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, "u"), null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    private MockMultipartFile csv() {
        return new MockMultipartFile("archivo", "productos.csv", "text/csv",
                "descripcion\nFiltro".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importarProductos_comoAdmin_devuelve202ConJobId() throws Exception {
        when(jobRegistry.crear(anyInt())).thenReturn(new ImportJob("job-123", 1));

        mvc.perform(multipart("/api/v1/importaciones/productos").file(csv()).with(authentication(auth("ADMIN"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
    }

    @Test
    @WithMockUser(roles = "OPERARIO")
    void importarProductos_comoOperario_devuelve403() throws Exception {
        mvc.perform(multipart("/api/v1/importaciones/productos").file(csv()))
                .andExpect(status().isForbidden());
    }
}
