package com.partvision.auth.controller;

import com.partvision.auth.domain.Rol;
import com.partvision.auth.domain.Usuario;
import com.partvision.auth.dto.TotpSetupResponse;
import com.partvision.auth.security.JwtAuthenticationFilter;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.RestAuthenticationEntryPoint;
import com.partvision.auth.security.SecurityConfig;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.auth.service.TwoFactorService;
import com.partvision.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TwoFactorController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        RestAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "security.jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
        "security.jwt.expiration-ms=3600000"
})
class TwoFactorControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private TwoFactorService twoFactorService;
    @MockBean
    private TokenRevocationService revocationService;

    private String bearer() {
        Usuario u = Usuario.builder()
                .id(1L).username("testuser")
                .roles(Set.of(Rol.builder().nombre("OPERARIO").build()))
                .build();
        return "Bearer " + jwtService.generateToken(u);
    }

    @Test
    void status_devuelveEstado2FA() throws Exception {
        when(twoFactorService.estaActivo(1L)).thenReturn(true);

        mvc.perform(get("/api/v1/2fa/status")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void status_sinActivar() throws Exception {
        when(twoFactorService.estaActivo(1L)).thenReturn(false);

        mvc.perform(get("/api/v1/2fa/status")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void setup_devuelveSecretYUri() throws Exception {
        when(twoFactorService.iniciarSetup(1L))
                .thenReturn(new TotpSetupResponse("SECRETO", "otpauth://totp/test?secret=SECRETO"));

        mvc.perform(post("/api/v1/2fa/setup")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRETO"))
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/test?secret=SECRETO"));
    }

    @Test
    void activate_devuelveRecoveryCodes() throws Exception {
        when(twoFactorService.activar(1L, "123456")).thenReturn(List.of("code-1", "code-2"));

        mvc.perform(post("/api/v1/2fa/activate")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes[0]").value("code-1"))
                .andExpect(jsonPath("$.recoveryCodes[1]").value("code-2"));
    }

    @Test
    void activate_codeVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/2fa/activate")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disable_devuelve204() throws Exception {
        mvc.perform(post("/api/v1/2fa/disable")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isNoContent());

        verify(twoFactorService).desactivar(1L, "123456");
    }

    @Test
    void disable_codeVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/2fa/disable")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinToken_devuelve401() throws Exception {
        mvc.perform(get("/api/v1/2fa/status"))
                .andExpect(status().isUnauthorized());
    }
}
