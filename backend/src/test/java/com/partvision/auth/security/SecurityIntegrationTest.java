package com.partvision.auth.security;

import com.partvision.auth.controller.UsuarioController;
import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.UsuarioResponse;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @MockBean
    private AuthService authService;
    @MockBean
    private TokenRevocationService revocationService;

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
                .thenReturn(new UsuarioResponse(1L, "admin", "Admin", null, true, Set.of("ADMIN")));

        mvc.perform(get("/api/v1/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    /**
     * Panel web (auth por cookie): la mutacion pasa. No hay double-submit CSRF (se protege con
     * la cookie SameSite=Strict), asi que un POST autenticado por cookie no necesita token.
     */
    @Test
    void postConCookie_autenticaYCrea() throws Exception {
        String token = jwtService.generateToken(admin());
        when(authService.crearUsuario(any()))
                .thenReturn(new UsuarioResponse(3L, "nuevo", "Juan", null, true, Set.of("OPERARIO")));

        mvc.perform(post("/api/v1/usuarios")
                        .cookie(new Cookie("pv_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Juan","email":"nuevo@test.com"}"""))
                .andExpect(status().isCreated());
    }

    /** Auth por header Bearer (herramientas/tests): el POST tambien pasa. */
    @Test
    void postConBearer_autenticaYCrea() throws Exception {
        String token = jwtService.generateToken(admin());
        when(authService.crearUsuario(any()))
                .thenReturn(new UsuarioResponse(2L, "nuevo", "Juan", null, true, Set.of("OPERARIO")));

        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nuevo","password":"password123","nombre":"Juan","email":"nuevo2@test.com"}"""))
                .andExpect(status().isCreated());
    }

    private static Usuario admin() {
        return Usuario.builder()
                .id(1L).username("admin")
                .roles(Set.of(Rol.builder().nombre("ADMIN").build()))
                .build();
    }
}
