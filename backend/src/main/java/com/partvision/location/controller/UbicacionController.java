package com.partvision.location.controller;

import com.partvision.location.dto.UbicacionRequest;
import com.partvision.location.dto.UbicacionResponse;
import com.partvision.location.service.UbicacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ubicaciones")
@RequiredArgsConstructor
public class UbicacionController {

    private final UbicacionService ubicacionService;

    @PostMapping
    public ResponseEntity<UbicacionResponse> create(@Valid @RequestBody UbicacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ubicacionService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UbicacionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ubicacionService.findById(id));
    }

    /** Ubicaciones raiz (sin padre). */
    @GetMapping
    public ResponseEntity<List<UbicacionResponse>> findRaices() {
        return ResponseEntity.ok(ubicacionService.findRaices());
    }

    /** Hijos directos de una ubicacion (navegacion del arbol). */
    @GetMapping("/{id}/hijos")
    public ResponseEntity<List<UbicacionResponse>> findHijos(@PathVariable Long id) {
        return ResponseEntity.ok(ubicacionService.findHijos(id));
    }
}
