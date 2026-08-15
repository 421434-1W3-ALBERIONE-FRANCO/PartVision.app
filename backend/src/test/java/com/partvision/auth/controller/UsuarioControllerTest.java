package com.partvision.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.RegisterRequest;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.security.JwtAuthenticationFilter;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.RestAuthenticationEntryPoint;
import com.partvision.auth.security.SecurityConfig;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los endpoints mutantes se ejercen autenticando por header {@code Bearer} (auth de
 * herramientas/API): asi el JWT valido aporta el rol y ademas la request queda exenta de
 * CSRF (ver {@code SecurityConfig}). La proteccion CSRF del panel web se testea aparte en
 * {@code SecurityIntegrationTest}.
 */
@WebMvcTest(controllers = UsuarioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, RestAuthenticationEntryPoint.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class UsuarioControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UsuarioService usuarioService;
    @MockBean
    private AuthService authService;

    private String bearerAdmin(Long id) {
        Usuario admin = Usuario.builder()
                .id(id).username("admin")
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();
        return "Bearer " + jwtService.generateToken(admin);
    }

    @Test
    void listar_comoAdmin_devuelve200() throws Exception {
        UsuarioResponse u1 = new UsuarioResponse(1L, "admin", "Admin", true, Set.of("ADMIN"));
        when(authService.listarTodos()).thenReturn(java.util.List.of(u1));

        mvc.perform(get("/api/v1/usuarios").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @WithMockUser(roles = "OPERARIO")
    void listar_comoOperario_devuelve403() throws Exception {
        mvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_comoAdmin_devuelve201() throws Exception {
        RegisterRequest req = new RegisterRequest("operario2", "password123", "Operario Dos");
        UsuarioResponse res = new UsuarioResponse(2L, "operario2", "Operario Dos", true, Set.of("OPERARIO"));
        when(authService.crearUsuario(any(RegisterRequest.class))).thenReturn(res);

        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operario2"));
    }

    @Test
    void toggleActivo_otroUsuario_devuelve200() throws Exception {
        UsuarioResponse res = new UsuarioResponse(2L, "operario", "Operario", false, Set.of("OPERARIO"));
        when(authService.toggleActivo(eq(2L))).thenReturn(res);

        mvc.perform(patch("/api/v1/usuarios/2/activo").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void toggleActivo_mismoUsuario_devuelve422() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/1/activo").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isUnprocessableEntity());
    }
}
