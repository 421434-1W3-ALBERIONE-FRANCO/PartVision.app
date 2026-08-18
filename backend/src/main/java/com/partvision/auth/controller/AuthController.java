package com.partvision.auth.controller;

import com.partvision.auth.dto.ForgotPasswordRequest;
import com.partvision.auth.dto.LoginRequest;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.dto.ResetPasswordRequest;
import com.partvision.auth.dto.TwoFactorRecoverRequest;
import com.partvision.auth.dto.TwoFactorRecoverConfirmRequest;
import com.partvision.auth.security.AuthCookieFactory;
import com.partvision.auth.security.JwtService;
import com.partvision.auth.security.TokenRevocationService;
import com.partvision.auth.service.AuthService;
import com.partvision.auth.service.PasswordResetService;
import com.partvision.auth.service.TwoFactorRecoveryService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación pública. El alta de usuarios NO es pública: solo un ADMIN puede
 * crear cuentas desde {@code UsuarioController} (evita el auto-registro abierto en
 * internet). Este controller expone el login y el logout.
 *
 * <p>El login setea el JWT en una cookie {@code HttpOnly} y lo devuelve en el body. El
 * logout borra esa cookie Y revoca el token del lado del servidor (su jti queda en la
 * lista negra hasta que expira), asi un token capturado deja de servir tras el logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;
    private final JwtService jwtService;
    private final TokenRevocationService revocationService;
    private final PasswordResetService passwordResetService;
    private final TwoFactorRecoveryService twoFactorRecoveryService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse body = authService.login(request);
        ResponseCookie cookie = cookieFactory.create(body.token(), body.expiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.solicitarReset(request.email());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Si el email está registrado, recibirás un enlace para restablecer tu contraseña"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetearPassword(request.token(), request.password());
        return ResponseEntity.ok(java.util.Map.of("message", "Contraseña actualizada correctamente"));
    }

    @PostMapping("/2fa/recover-request")
    public ResponseEntity<java.util.Map<String, String>> twoFactorRecoverRequest(
            @Valid @RequestBody TwoFactorRecoverRequest request) {
        twoFactorRecoveryService.solicitarRecuperacion(request.email());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Si el email está registrado y tiene 2FA activo, recibirás un código de recuperación"));
    }

    @PostMapping("/2fa/recover-confirm")
    public ResponseEntity<java.util.Map<String, String>> twoFactorRecoverConfirm(
            @Valid @RequestBody TwoFactorRecoverConfirmRequest request) {
        twoFactorRecoveryService.confirmarRecuperacion(request.email(), request.code());
        return ResponseEntity.ok(java.util.Map.of("message", "2FA desactivado. Podés iniciar sesión y re-configurarlo"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        revocarTokenActual(request);
        ResponseCookie cleared = cookieFactory.clear();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    /** Si la request trae un token valido, lo agrega a la lista negra (revocacion). */
    private void revocarTokenActual(HttpServletRequest request) {
        String token = tokenDesdeRequest(request);
        if (token == null) {
            return;
        }
        try {
            Claims claims = jwtService.parse(token);
            revocationService.revocar(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Token invalido o ya expirado: no hay nada que revocar.
        }
    }

    private String tokenDesdeRequest(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieFactory.name().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
