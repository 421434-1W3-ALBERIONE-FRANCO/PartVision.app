package com.partvision.pricing;

import com.partvision.pricing.dto.PrecioBatchResponse;
import com.partvision.pricing.dto.PrecioImportColumnasResponse;
import com.partvision.pricing.dto.PrecioImportPreviewResponse;
import com.partvision.pricing.dto.PrecioImportProgresoResponse;
import com.partvision.pricing.dto.PrecioImportResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/precios/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PrecioImportController {

    private final PrecioImportService importService;

    // ── DEBUG: endpoint de prueba para verificar que multipart funciona ──
    @PostMapping(value = "/test-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> testUpload(
            @RequestPart(value = "archivo", required = false) MultipartFile archivo) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ok", true);
        info.put("archivoRecibido", archivo != null);
        if (archivo != null) {
            info.put("nombre", archivo.getOriginalFilename());
            info.put("tamano", archivo.getSize());
            info.put("contentType", archivo.getContentType());
            try {
                byte[] bytes = archivo.getBytes();
                info.put("bytesLeidos", bytes.length);
            } catch (Exception e) {
                info.put("errorAlLeer", e.getClass().getName() + ": " + e.getMessage());
            }
        }
        info.put("tmpDir", System.getProperty("java.io.tmpdir"));
        info.put("tmpDirExists", new java.io.File(System.getProperty("java.io.tmpdir")).exists());
        info.put("uploadDirExists", new java.io.File("/tmp/partvision-uploads").exists());
        return ResponseEntity.ok(info);
    }

    @PostMapping("/columnas")
    public ResponseEntity<?> detectarColumnas(
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            log.info("=== UPLOAD DEBUG === archivo={}, size={}, contentType={}",
                    archivo.getOriginalFilename(), archivo.getSize(), archivo.getContentType());

            byte[] bytes = archivo.getBytes();
            log.info("=== UPLOAD DEBUG === bytes leidos: {}", bytes.length);

            String nombre = archivo.getOriginalFilename();
            log.info("=== UPLOAD DEBUG === llamando detectarColumnas...");

            PrecioImportColumnasResponse res = importService.detectarColumnas(bytes, nombre);
            log.info("=== UPLOAD DEBUG === OK: uploadId={}, columnas={}, filas={}",
                    res.uploadId(), res.columnas(), res.totalFilas());

            return ResponseEntity.ok(res);
        } catch (Throwable t) {
            // Capturamos TODO y devolvemos el stack trace completo
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            log.error("=== UPLOAD ERROR === {}", stackTrace);

            Map<String, Object> error = new LinkedHashMap<>();
            error.put("message", "DEBUG: " + t.getClass().getName() + ": " + t.getMessage());
            error.put("rootCause", getRootCause(t));
            error.put("stackTrace", stackTrace.substring(0, Math.min(stackTrace.length(), 2000)));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    private String getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getName() + ": " + root.getMessage();
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
