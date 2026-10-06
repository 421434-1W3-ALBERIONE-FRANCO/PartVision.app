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
    private final RecepcionListaService recepcion;

    /** @param proveedor de que lista; sin decir, la de ADS */
    @GetMapping
    public SincronizacionEstadoResponse estado(@RequestParam(required = false) String proveedor) {
        return service.estado(proveedor);
    }

    /** "Actualizar ahora". Con {@code forzar} aplica una lista que habia quedado retenida. */
    @PostMapping
    public ResponseEntity<SincronizacionResponse> actualizarAhora(@RequestParam(defaultValue = "false") boolean forzar) {
        return ResponseEntity.accepted().body(service.iniciarManual(forzar));
    }

    /**
     * "Aplicar igual" a la lista de EGSA que quedo retenida: usa el archivo guardado, porque a
     * EGSA no se la puede volver a pedir.
     */
    @PostMapping("/retenida/aplicar")
    public ResponseEntity<SincronizacionResponse> aplicarRetenida() {
        return ResponseEntity.accepted().body(recepcion.aplicarRetenida());
    }

    /** El aviso de la barra superior: 204 si no hay nada que mirar. */
    @GetMapping("/alerta")
    public ResponseEntity<AlertaPreciosResponse> alerta() {
        return service.alerta().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/revisiones")
    public List<PrecioRevisionResponse> revisiones(@RequestParam(required = false) String proveedor) {
        return service.revisionesPendientes(proveedor);
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
