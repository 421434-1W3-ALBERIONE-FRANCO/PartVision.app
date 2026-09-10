package com.partvision.pricing;

import com.partvision.pricing.dto.PrecioBatchResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportProgresoResponse;
import com.partvision.pricing.dto.PrecioImportResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/precios/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PrecioImportController {

    private final PrecioImportService importService;

    @PostMapping("/columnas")
    public ResponseEntity<PrecioImportColumnasResponse> detectarColumnas(
            @RequestParam("archivo") MultipartFile archivo) throws IOException {
        return ResponseEntity.ok(importService.detectarColumnas(archivo.getBytes(),
                archivo.getOriginalFilename()));
    }

    @PostMapping("/preview")
    public ResponseEntity<PrecioImportPreviewResponse> preview(
            @RequestParam String uploadId,
            @RequestParam String colSku,
            @RequestParam String colPrecio,
            @RequestParam String proveedor) {
        return ResponseEntity.ok(importService.preview(uploadId, colSku, colPrecio, proveedor));
    }

    @PostMapping("/aplicar")
    public ResponseEntity<?> aplicar(
            @RequestParam String uploadId,
            @RequestParam String colSku,
            @RequestParam String colPrecio,
            @RequestParam String proveedor,
            @RequestParam(required = false) Set<String> excluidos,
            @RequestParam(required = false) String archivo) {
        importService.validarAplicar(uploadId, proveedor);
        if (!importService.iniciarImport()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ya hay una importación en curso"));
        }
        importService.ejecutarImportAsync(uploadId, colSku, colPrecio, proveedor, excluidos, archivo);
        return ResponseEntity.accepted()
                .body(Map.of("message", "Importación iniciada en segundo plano"));
    }

    @GetMapping("/progreso")
    public PrecioImportProgresoResponse progreso() {
        return importService.getProgresoImport();
    }

    @GetMapping("/batches")
    public List<PrecioBatchResponse> listarBatches() {
        return importService.listarBatches();
    }

    @PostMapping("/batches/{id}/rollback")
    public ResponseEntity<PrecioBatchResponse> rollback(@PathVariable Long id) {
        return ResponseEntity.ok(importService.rollback(id));
    }
}
