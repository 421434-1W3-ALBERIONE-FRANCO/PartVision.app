package com.partvision.imports.controller;

import com.partvision.imports.dto.ImportResultResponse;
import com.partvision.imports.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/importaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Carga masiva: solo administradores (el cliente no toca la BD).
public class ImportController {

    private final ImportService importService;

    /** Importacion masiva de productos desde un CSV (multipart/form-data, campo "archivo"). */
    @PostMapping("/productos")
    public ResponseEntity<ImportResultResponse> importarProductos(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(importService.importarCsv(archivo));
    }
}
