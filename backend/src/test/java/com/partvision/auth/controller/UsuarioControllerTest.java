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
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.UsuarioService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @MockBean
    private TokenRevocationService revocationService;

    private String bearerAdmin(Long id) {
        Usuario admin = Usuario.builder()
                .id(id).username("admin")
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();
        return "Bearer " + jwtService.generateToken(admin);
    }

    private String bearerOperario(Long id) {
        Usuario operario = Usuario.builder()
                .id(id).username("operario")
                .roles(Set.of(Rol.builder().nombre("OPERARIO").build()))
                .build();
        return "Bearer " + jwtService.generateToken(operario);
    }

    private UsuarioResponse sampleAdmin() {
        return new UsuarioResponse(1L, "admin", "Admin", "admin@test.com", true, Set.of("ADMIN"));
    }

    private UsuarioResponse sampleOperario() {
        return new UsuarioResponse(2L, "operario", "Operario", "op@test.com", true, Set.of("OPERARIO"));
    }

    // ---- GET /api/v1/usuarios/me ----

    @Test
    void me_autenticado_devuelve200() throws Exception {
        when(usuarioService.getCurrentUser()).thenReturn(sampleAdmin());

        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }

    @Test
    void me_sinAuth_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---- PATCH /api/v1/usuarios/me/password ----

    @Test
    void cambiarPassword_devuelve200() throws Exception {
        doNothing().when(authService).cambiarPasswordPropia(eq(1L), anyString(), anyString(), any());

        mvc.perform(patch("/api/v1/usuarios/me/password")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"oldPass123","nuevaPassword":"newPass123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña actualizada correctamente"));
    }

    @Test
    void cambiarPassword_passwordActualVacia_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/password")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"","nuevaPassword":"newPass123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarPassword_nuevaPasswordCorta_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/password")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"oldPass123","nuevaPassword":"short"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarPassword_sinAuth_devuelve401() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"oldPass123","nuevaPassword":"newPass123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarPassword_passwordIncorrecta_devuelve422() throws Exception {
        doThrow(new BusinessException("La contraseña actual es incorrecta"))
                .when(authService).cambiarPasswordPropia(eq(1L), anyString(), anyString(), any());

        mvc.perform(patch("/api/v1/usuarios/me/password")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"wrong","nuevaPassword":"newPass123"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- GET /api/v1/usuarios ----

    @Test
    void listar_comoAdmin_devuelve200() throws Exception {
        UsuarioResponse u1 = new UsuarioResponse(1L, "admin", "Admin", null, true, Set.of("ADMIN"));
        when(authService.listarTodos()).thenReturn(List.of(u1));

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
    void listar_sinAuth_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /api/v1/usuarios ----

    @Test
    void crear_comoAdmin_devuelve201() throws Exception {
        RegisterRequest req = new RegisterRequest("operario2", "password123", "Operario Dos", "op2@test.com");
        UsuarioResponse res = new UsuarioResponse(2L, "operario2", "Operario Dos", null, true, Set.of("OPERARIO"));
        when(authService.crearUsuario(any(RegisterRequest.class))).thenReturn(res);

        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operario2"));
    }

    @Test
    void crear_usernameVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"password123","nombre":"Test","email":"t@t.com"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_passwordCorta_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"short","nombre":"Test","email":"t@t.com"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_emailInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Test","email":"no-valido"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_comoOperario_devuelve403() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearerOperario(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Test","email":"t@t.com"}"""))
                .andExpect(status().isForbidden());
    }

    // ---- PATCH /api/v1/usuarios/{id}/activo ----

    @Test
    void toggleActivo_otroUsuario_devuelve200() throws Exception {
        UsuarioResponse res = new UsuarioResponse(2L, "operario", "Operario", null, false, Set.of("OPERARIO"));
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

    @Test
    void toggleActivo_comoOperario_devuelve403() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/activo").header("Authorization", bearerOperario(5L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleActivo_usuarioInexistente_devuelve404() throws Exception {
        when(authService.toggleActivo(eq(99L)))
                .thenThrow(new ResourceNotFoundException("Usuario", 99L));

        mvc.perform(patch("/api/v1/usuarios/99/activo").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE /api/v1/usuarios/{id} ----

    @Test
    void eliminar_otroUsuario_devuelve204() throws Exception {
        doNothing().when(authService).eliminarUsuario(eq(2L));

        mvc.perform(delete("/api/v1/usuarios/2").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isNoContent());

        verify(authService).eliminarUsuario(2L);
    }

    @Test
    void eliminar_mismoUsuario_devuelve422() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/1").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void eliminar_comoOperario_devuelve403() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/2").header("Authorization", bearerOperario(5L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminar_usuarioInexistente_devuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("Usuario", 99L))
                .when(authService).eliminarUsuario(eq(99L));

        mvc.perform(delete("/api/v1/usuarios/99").header("Authorization", bearerAdmin(1L)))
                .andExpect(status().isNotFound());
    }

    // ---- PATCH /api/v1/usuarios/{id}/email ----

    @Test
    void cambiarEmail_devuelve200() throws Exception {
        UsuarioResponse res = new UsuarioResponse(2L, "operario", "Operario", "nuevo@test.com", true, Set.of("OPERARIO"));
        when(authService.cambiarEmail(eq(2L), eq("nuevo@test.com"))).thenReturn(res);

        mvc.perform(patch("/api/v1/usuarios/2/email")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@test.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo@test.com"));
    }

    @Test
    void cambiarEmail_emailInvalido_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/email")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-valido"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarEmail_emailVacio_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/email")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarEmail_comoOperario_devuelve403() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/email")
                        .header("Authorization", bearerOperario(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@test.com"}"""))
                .andExpect(status().isForbidden());
    }

    // ---- PATCH /api/v1/usuarios/{id}/rol ----

    @Test
    void cambiarRol_otroUsuario_devuelve200() throws Exception {
        UsuarioResponse res = new UsuarioResponse(2L, "operario", "Operario", null, true, Set.of("ADMIN"));
        when(authService.cambiarRol(eq(2L), eq("ADMIN"))).thenReturn(res);

        mvc.perform(patch("/api/v1/usuarios/2/rol")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void cambiarRol_mismoUsuario_devuelve422() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/1/rol")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"OPERARIO"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cambiarRol_rolVacio_devuelve400() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/rol")
                        .header("Authorization", bearerAdmin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cambiarRol_comoOperario_devuelve403() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/2/rol")
                        .header("Authorization", bearerOperario(5L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"ADMIN"}"""))
                .andExpect(status().isForbidden());
    }
}
