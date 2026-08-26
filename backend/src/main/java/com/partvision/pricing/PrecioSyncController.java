package com.partvision.pricing;

import com.partvision.pricing.dto.SyncResultResponse;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<SyncResultResponse> sincronizar() {
        return ResponseEntity.ok(syncService.sincronizar());
    }

    @GetMapping("/sync/estado")
    public ResponseEntity<Map<String, Boolean>> estado() {
        return ResponseEntity.ok(Map.of("sincronizando", syncService.isSincronizando()));
    }
}
