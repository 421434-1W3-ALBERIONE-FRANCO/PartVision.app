package com.partvision.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.security.AuthCookieFactory;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.PasswordResetService;
import com.partvision.auth.service.TwoFactorRecoveryService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.GlobalExceptionHandler;
import com.partvision.common.exception.InvalidCredentialsException;
import com.partvision.common.exception.TwoFactorRequiredException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AuthCookieFactory.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AuthService authService;
    // El filtro JWT es un bean Filter que @WebMvcTest incluye; necesita estos colaboradores.
    @MockBean
    private JwtService jwtService;
    @MockBean
    private TokenRevocationService revocationService;
    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
    private TwoFactorRecoveryService twoFactorRecoveryService;

    // ---- POST /api/v1/auth/login ----

    @Test
    void login_devuelve200ConTokenYCookieHttpOnly() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-jwt", 3600L));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-jwt"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(header().string("Set-Cookie", containsString("pv_token=token-jwt")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
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

    @Test
    void login_usernameVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"admin1234"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_passwordVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_bodyVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_requiere2FA_devuelve401ConFlag() throws Exception {
        when(authService.login(any()))
                .thenThrow(new TwoFactorRequiredException("Se requiere codigo 2FA"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void login_conCodigo2FA_devuelve200() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-2fa", 3600L));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin1234","code":"123456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-2fa"));
    }

    // ---- POST /api/v1/auth/logout ----

    @Test
    void logout_borraLaCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("pv_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logout_conBearerToken_revocaYBorraCookie() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-123");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.parse("valid-token")).thenReturn(claims);

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(revocationService).revocar(eq("jti-123"), any(Instant.class));
    }

    @Test
    void logout_conCookieToken_revocaYBorraCookie() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-cookie");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.parse("cookie-token")).thenReturn(claims);

        mvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("pv_token", "cookie-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(revocationService).revocar(eq("jti-cookie"), any(Instant.class));
    }

    @Test
    void logout_sinToken_noRevoca() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        verify(revocationService, never()).revocar(anyString(), any(Instant.class));
    }

    @Test
    void logout_tokenInvalido_noRevocaPeroDevuelve204() throws Exception {
        when(jwtService.parse("bad-token"))
                .thenThrow(new io.jsonwebtoken.JwtException("Token invalido"));

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isNoContent());

        verify(revocationService, never()).revocar(anyString(), any(Instant.class));
    }

    // ---- POST /api/v1/auth/forgot-password ----

    @Test
    void forgotPassword_devuelve200() throws Exception {
        doNothing().when(passwordResetService).solicitarReset("user@test.com");

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si el email está registrado, recibirás un enlace para restablecer tu contraseña"));
    }

    @Test
    void forgotPassword_emailVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPassword_emailInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-valido"}"""))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /api/v1/auth/reset-password ----

    @Test
    void resetPassword_devuelve200() throws Exception {
        doNothing().when(passwordResetService).resetearPassword("reset-token", "newPass123");

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"reset-token","password":"newPass123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña actualizada correctamente"));
    }

    @Test
    void resetPassword_tokenVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"","password":"newPass123"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_passwordCorta_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"reset-token","password":"short"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_tokenInvalido_devuelve422() throws Exception {
        doThrow(new BusinessException("Token de recuperación inválido o expirado"))
                .when(passwordResetService).resetearPassword(eq("bad-token"), anyString());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"bad-token","password":"newPass123"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- POST /api/v1/auth/2fa/recover-request ----

    @Test
    void twoFactorRecoverRequest_devuelve200() throws Exception {
        doNothing().when(twoFactorRecoveryService).solicitarRecuperacion("user@test.com");

        mvc.perform(post("/api/v1/auth/2fa/recover-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Si el email está registrado y tiene 2FA activo, recibirás un código de recuperación"));
    }

    @Test
    void twoFactorRecoverRequest_emailVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/recover-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoFactorRecoverRequest_emailInvalido_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/recover-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-valido"}"""))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /api/v1/auth/2fa/recover-confirm ----

    @Test
    void twoFactorRecoverConfirm_devuelve200() throws Exception {
        doNothing().when(twoFactorRecoveryService).confirmarRecuperacion("user@test.com", "123456");

        mvc.perform(post("/api/v1/auth/2fa/recover-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","code":"123456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "2FA desactivado. Podés iniciar sesión y re-configurarlo"));
    }

    @Test
    void twoFactorRecoverConfirm_emailVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/recover-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","code":"123456"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoFactorRecoverConfirm_codigoVacio_devuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/recover-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","code":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoFactorRecoverConfirm_codigoInvalido_devuelve422() throws Exception {
        doThrow(new BusinessException("Código de recuperación inválido o expirado"))
                .when(twoFactorRecoveryService).confirmarRecuperacion(eq("user@test.com"), eq("wrong"));

        mvc.perform(post("/api/v1/auth/2fa/recover-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@test.com","code":"wrong"}"""))
                .andExpect(status().isUnprocessableEntity());
    }
}
