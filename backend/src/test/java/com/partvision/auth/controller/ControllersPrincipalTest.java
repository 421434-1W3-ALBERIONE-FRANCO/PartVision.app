package com.partvision.auth.controller;

import com.partvision.auth.dto.CambiarRolRequest;
import com.partvision.auth.security.AuthCookieFactory;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.PasswordResetService;
import com.partvision.auth.service.TwoFactorRecoveryService;
import com.partvision.auth.service.UsuarioService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * De donde sale el token en el logout, y las protecciones "no contra vos mismo" de la
 * administracion de usuarios cuando el principal no es el esperado.
 */
class ControllersPrincipalTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthCookieFactory cookieFactory = new AuthCookieFactory("pv_token", true, "Strict");
    private final JwtService jwtService = mock(JwtService.class);
    private final TokenRevocationService revocationService = mock(TokenRevocationService.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);

    private AuthController authController() {
        return new AuthController(authService, cookieFactory, jwtService, revocationService,
                mock(PasswordResetService.class), mock(TwoFactorRecoveryService.class));
    }

    private UsuarioController usuarioController() {
        return new UsuarioController(usuarioService, authService);
    }

    private Authentication autenticacion(Object principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    // --- logout: de donde se saca el token a revocar ---

    @Test
    void logout_conHeaderBearer_revocaEseToken() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(jwtService.parse("abc")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc");

        authController().logout(request);

        verify(revocationService).revocar(anyString(), any());
    }

    @Test
    void logout_sinHeaderNiCookies_noRevocaNada() {
        authController().logout(new MockHttpServletRequest());

        verify(revocationService, never()).revocar(anyString(), any());
    }

    /** Un Authorization que no es Bearer no es un token: se ignora y se mira la cookie. */
    @Test
    void logout_headerQueNoEsBearer_miraLaCookie() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn("jti-2");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(jwtService.parse("de-la-cookie")).thenReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic xyz");
        request.setCookies(new Cookie("otra", "no"), new Cookie("pv_token", "de-la-cookie"));

        authController().logout(request);

        verify(jwtService).parse("de-la-cookie");
    }

    @Test
    void logout_cookiesSinLaDeAuth_noRevocaNada() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("otra", "no"));

        authController().logout(request);

        verify(revocationService, never()).revocar(anyString(), any());
    }

    // --- protecciones contra uno mismo ---

    @Test
    void toggleActivo_sobreSiMismo_seRechaza() {
        Authentication auth = autenticacion(new AuthenticatedUser(1L, "admin"));

        assertThatThrownBy(() -> usuarioController().toggleActivo(1L, auth))
                .isInstanceOf(BusinessException.class);
        verify(authService, never()).toggleActivo(anyLong());
    }

    /** Si el principal no es un AuthenticatedUser no hay a quien comparar: no bloquea. */
    @Test
    void toggleActivo_principalDeOtroTipo_noBloquea() {
        Authentication auth = autenticacion("un-string");

        assertThatCode(() -> usuarioController().toggleActivo(1L, auth)).doesNotThrowAnyException();
        verify(authService).toggleActivo(1L);
    }

    @Test
    void eliminar_sobreSiMismo_seRechaza() {
        Authentication auth = autenticacion(new AuthenticatedUser(1L, "admin"));

        assertThatThrownBy(() -> usuarioController().eliminar(1L, auth))
                .isInstanceOf(BusinessException.class);
        verify(authService, never()).eliminarUsuario(anyLong());
    }

    @Test
    void eliminar_principalDeOtroTipo_noBloquea() {
        Authentication auth = autenticacion("un-string");

        assertThatCode(() -> usuarioController().eliminar(1L, auth)).doesNotThrowAnyException();
        verify(authService).eliminarUsuario(1L);
    }

    @Test
    void cambiarRol_sobreSiMismo_seRechaza() {
        Authentication auth = autenticacion(new AuthenticatedUser(1L, "admin"));

        assertThatThrownBy(() -> usuarioController().cambiarRol(1L, new CambiarRolRequest("OPERARIO"), auth))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cambiarRol_principalDeOtroTipo_noBloquea() {
        Authentication auth = autenticacion("un-string");

        assertThatCode(() -> usuarioController().cambiarRol(1L, new CambiarRolRequest("OPERARIO"), auth))
                .doesNotThrowAnyException();
        verify(authService).cambiarRol(1L, "OPERARIO");
    }
}
