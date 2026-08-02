package com.partvision.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.service.AuthService;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AuthService authService;
    // El filtro JWT es un bean Filter que @WebMvcTest incluye; necesita este colaborador.
    @MockBean
    private JwtService jwtService;

    @Test
    void register_devuelve201() throws Exception {
        when(authService.register(any()))
                .thenReturn(new UsuarioResponse(1L, "nuevo", "Juan", true, Set.of("OPERARIO")));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Juan"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("nuevo"))
                .andExpect(jsonPath("$.roles[0]").value("OPERARIO"));
    }

    @Test
    void register_bodyInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"corta","nombre":"Juan"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("password"));
    }

    @Test
    void register_duplicado_devuelve409() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateResourceException("El username ya existe"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Juan"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void login_devuelve200ConToken() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-jwt", 3600L));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-jwt"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Credenciales invalidas"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"mala"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
