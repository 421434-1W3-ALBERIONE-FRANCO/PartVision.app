package com.partvision.auth.controller;

import com.partvision.auth.dto.ActivarTotpRequest;
import com.partvision.auth.dto.RecoveryCodesResponse;
import com.partvision.auth.dto.TotpSetupResponse;
import com.partvision.auth.dto.TotpStatusResponse;
import com.partvision.auth.service.TwoFactorService;
import com.partvision.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión del 2FA del usuario autenticado (cada uno maneja el suyo). Requiere sesión: por eso
 * NO cuelga de /api/v1/auth/** (público), sino de /api/v1/2fa (protegido).
 */
@RestController
@RequestMapping("/api/v1/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @GetMapping("/status")
    public TotpStatusResponse status(Authentication authentication) {
        return new TotpStatusResponse(twoFactorService.estaActivo(userId(authentication)));
    }

    /** Paso 1: genera el secret y devuelve el otpauth:// para el QR (aún no activa el 2FA). */
    @PostMapping("/setup")
    public TotpSetupResponse setup(Authentication authentication) {
        return twoFactorService.iniciarSetup(userId(authentication));
    }

    /** Paso 2: confirma con el primer código; activa el 2FA y devuelve los códigos de recuperación. */
    @PostMapping("/activate")
    public RecoveryCodesResponse activate(@Valid @RequestBody ActivarTotpRequest request,
                                          Authentication authentication) {
        return new RecoveryCodesResponse(twoFactorService.activar(userId(authentication), request.code()));
    }

    /** Desactiva el 2FA (exige un código válido). */
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody ActivarTotpRequest request,
                                        Authentication authentication) {
        twoFactorService.desactivar(userId(authentication), request.code());
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication authentication) {
        return ((AuthenticatedUser) authentication.getPrincipal()).id();
    }
}
