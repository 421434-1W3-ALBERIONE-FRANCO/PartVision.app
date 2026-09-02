package com.partvision.compras;

import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.dto.CambiarEstadoRequest;
import com.partvision.compras.dto.CompraResponse;
import com.partvision.compras.dto.RecepcionCompraRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;

    @Value("${partvision.compras.api-key:}")
    private String apiKey;

    @PostMapping("/recepcion")
    public ResponseEntity<CompraResponse> recepcion(
            @RequestHeader(value = "X-API-Key", required = false) String key,
            @Valid @RequestBody RecepcionCompraRequest request) {

        validarApiKey(key);
        CompraResponse response = compraService.registrarRecepcion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<CompraResponse> listar(
            @RequestParam(required = false) CompraEstado estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return compraService.listar(estado, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CompraResponse detalle(@PathVariable Long id) {
        return compraService.detalle(id);
    }

    @PatchMapping("/{id}/ingresar")
    @PreAuthorize("hasRole('ADMIN')")
    public CompraResponse marcarIngresada(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return compraService.marcarIngresada(id, request);
    }

    private void validarApiKey(String key) {
        if (apiKey.isBlank()) return;
        if (key == null || !key.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key inválida");
        }
    }
}
