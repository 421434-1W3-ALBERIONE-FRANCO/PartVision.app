package com.partvision.auth.controller;

import com.partvision.auth.dto.LoginRequest;
import com.partvision.auth.dto.LoginResponse;
import com.partvision.auth.security.AuthCookieFactory;
import com.partvision.auth.service.AuthService;
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
 * <p>El login setea el JWT en una cookie {@code HttpOnly} (para el panel web, que asi
 * nunca expone el token al JavaScript) y además lo devuelve en el body. El logout borra
 * esa cookie. Los roles para el UI se leen aparte desde {@code GET /api/v1/usuarios/me}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse body = authService.login(request);
        ResponseCookie cookie = cookieFactory.create(body.token(), body.expiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = cookieFactory.clear();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }
}
