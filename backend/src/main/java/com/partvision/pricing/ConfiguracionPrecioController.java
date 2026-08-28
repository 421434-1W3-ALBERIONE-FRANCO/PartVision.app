package com.partvision.pricing;

import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.dto.ConfiguracionPrecioRequest;
import com.partvision.pricing.dto.ConfiguracionPrecioResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/precios/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracionPrecioController {

    private final ConfiguracionPrecioRepository repo;

    @GetMapping
    public List<ConfiguracionPrecioResponse> listar() {
        return repo.findAll().stream()
                .map(ConfiguracionPrecioResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ConfiguracionPrecioResponse> crear(@Valid @RequestBody ConfiguracionPrecioRequest req) {
        if (req.proveedor() == null || req.proveedor().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (repo.findByProveedorIgnoreCase(req.proveedor()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        ConfiguracionPrecio config = new ConfiguracionPrecio();
        config.setProveedor(req.proveedor());
        config.setMargen(req.margen());
        config.setActivo(req.activo() != null ? req.activo() : true);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConfiguracionPrecioResponse.from(repo.save(config)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracionPrecioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ConfiguracionPrecioRequest req
    ) {
        return repo.findById(id)
                .map(config -> {
                    config.setMargen(req.margen());
                    if (req.activo() != null) {
                        config.setActivo(req.activo());
                    }
                    return ResponseEntity.ok(ConfiguracionPrecioResponse.from(repo.save(config)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
