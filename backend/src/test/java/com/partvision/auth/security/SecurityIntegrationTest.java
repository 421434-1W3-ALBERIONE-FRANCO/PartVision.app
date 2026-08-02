package com.partvision.auth.security;

import com.partvision.auth.controller.UsuarioController;
import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica la cadena de seguridad completa (SecurityConfig + filtro JWT +
 * entry point) sobre un endpoint protegido, sin necesidad de base de datos.
 */
@WebMvcTest(controllers = UsuarioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UsuarioService usuarioService;

    @Test
    void endpointProtegido_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No autenticado"));
    }

    @Test
    void endpointProtegido_tokenInvalido_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_conTokenValido_devuelve200() throws Exception {
        Usuario usuario = Usuario.builder()
                .id(1L).username("admin")
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();
        String token = jwtService.generateToken(usuario);
        when(usuarioService.getCurrentUser())
                .thenReturn(new UsuarioResponse(1L, "admin", "Admin", true, Set.of("ADMIN")));

        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }
}
