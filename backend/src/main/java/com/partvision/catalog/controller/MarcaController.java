package com.partvision.catalog.controller;

import com.partvision.catalog.dto.MarcaRequest;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.service.MarcaService;
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
@RequestMapping("/api/v1/marcas")
@RequiredArgsConstructor
public class MarcaController {

    private final MarcaService marcaService;

    @PostMapping
    public ResponseEntity<MarcaResponse> create(@Valid @RequestBody MarcaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(marcaService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<MarcaResponse>> findAll() {
        return ResponseEntity.ok(marcaService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MarcaResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(marcaService.findById(id));
    }
}
