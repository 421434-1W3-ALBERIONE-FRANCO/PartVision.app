package com.partvision.pricing;

import com.partvision.pricing.dto.SyncResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/precios")
@RequiredArgsConstructor
public class PrecioSyncController {

    private final PrecioSyncService syncService;

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sincronizar() {
        if (!syncService.iniciarSync()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya hay una sincronización en curso"));
        }
        syncService.ejecutarSyncAsync();
        return ResponseEntity.accepted()
                .body(Map.of("message", "Sincronización iniciada en segundo plano"));
    }

    @GetMapping("/sync/estado")
    public ResponseEntity<Map<String, Object>> estado() {
        boolean sincronizando = syncService.isSincronizando();
        SyncResultResponse ultimo = syncService.getUltimoResultado();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("sincronizando", sincronizando);
        if (ultimo != null) {
            body.put("ultimoResultado", ultimo);
        }
        return ResponseEntity.ok(body);
    }
}
