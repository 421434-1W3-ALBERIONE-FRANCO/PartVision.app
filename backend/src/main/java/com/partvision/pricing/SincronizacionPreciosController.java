package com.partvision.pricing;

import com.partvision.pricing.dto.AlertaPreciosResponse;
import com.partvision.pricing.dto.PrecioRevisionResponse;
import com.partvision.pricing.dto.RevisionPreciosRequest;
import com.partvision.pricing.dto.RevisionPreciosResponse;
import com.partvision.pricing.dto.SincronizacionEstadoResponse;
import com.partvision.pricing.dto.SincronizacionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Actualizacion automatica de precios desde el portal del proveedor (pantalla Precios). */
@RestController
@RequestMapping("/api/v1/precios/sincronizacion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SincronizacionPreciosController {

    private final SincronizacionPreciosService service;

    @GetMapping
    public SincronizacionEstadoResponse estado() {
        return service.estado();
    }

    /** "Actualizar ahora". Con {@code forzar} aplica una lista que habia quedado retenida. */
    @PostMapping
    public ResponseEntity<SincronizacionResponse> actualizarAhora(@RequestParam(defaultValue = "false") boolean forzar) {
        return ResponseEntity.accepted().body(service.iniciarManual(forzar));
    }

    /** El aviso de la barra superior: 204 si no hay nada que mirar. */
    @GetMapping("/alerta")
    public ResponseEntity<AlertaPreciosResponse> alerta() {
        return service.alerta().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/revisiones")
    public List<PrecioRevisionResponse> revisiones() {
        return service.revisionesPendientes();
    }

    @PostMapping("/revisiones/aplicar")
    public RevisionPreciosResponse aplicar(@RequestBody(required = false) RevisionPreciosRequest req) {
        return service.aplicarRevisiones(req == null ? null : req.ids());
    }

    @PostMapping("/revisiones/descartar")
    public RevisionPreciosResponse descartar(@RequestBody(required = false) RevisionPreciosRequest req) {
        return service.descartarRevisiones(req == null ? null : req.ids());
    }
}
