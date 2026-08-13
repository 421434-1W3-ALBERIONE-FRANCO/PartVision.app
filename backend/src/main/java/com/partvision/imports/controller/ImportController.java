package com.partvision.imports.controller;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.imports.dto.ImportJobResponse;
import com.partvision.imports.service.CatalogoMantenimientoService;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ImportJobRegistry;
import com.partvision.imports.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/importaciones")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Carga masiva: solo administradores (el cliente no toca la BD).
public class ImportController {

    private final ImportService importService;
    private final ImportJobRegistry jobRegistry;
    private final CatalogoMantenimientoService catalogoMantenimientoService;

    /**
     * Inicia una importacion masiva ASINCRONA (multipart/form-data, campo "archivo").
     * Devuelve 202 con el jobId; el archivo se procesa en segundo plano y el progreso
     * se consulta con {@link #estado}.
     */
    @PostMapping("/productos")
    public ResponseEntity<ImportJobResponse> importarProductos(@RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo esta vacio");
        }
        byte[] contenido = archivo.getBytes();
        ImportJob job = jobRegistry.crear(contarFilasDatos(contenido));
        importService.importarAsync(contenido, job);
        return ResponseEntity.accepted().body(ImportJobResponse.from(job));
    }

    /**
     * Vacia el catalogo (productos, codigos, stock y movimientos), PRESERVANDO ubicaciones.
     * Uso: reiniciar una carga que quedo con duplicados para volver a importar limpio.
     */
    @DeleteMapping("/catalogo")
    public ResponseEntity<Map<String, Object>> vaciarCatalogo() {
        int borrados = catalogoMantenimientoService.vaciarCatalogo();
        return ResponseEntity.ok(Map.of("productosBorrados", borrados));
    }

    /** Estado/progreso de una importacion (para el polling del panel). */
    @GetMapping("/productos/{jobId}")
    public ResponseEntity<ImportJobResponse> estado(@PathVariable String jobId) {
        ImportJob job = jobRegistry.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Importacion", jobId);
        }
        return ResponseEntity.ok(ImportJobResponse.from(job));
    }

    /** Estimacion de filas de datos (para el porcentaje): lineas menos el encabezado. */
    private int contarFilasDatos(byte[] contenido) {
        int lineas = 0;
        for (byte b : contenido) {
            if (b == '\n') {
                lineas++;
            }
        }
        return Math.max(0, lineas - 1);
    }
}
