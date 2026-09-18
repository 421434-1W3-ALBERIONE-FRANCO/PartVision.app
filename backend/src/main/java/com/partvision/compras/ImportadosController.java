package com.partvision.compras;

import com.partvision.compras.dto.AltaImportadoRequest;
import com.partvision.compras.dto.ImportadoPendienteResponse;
import com.partvision.compras.dto.ImportadoResueltoResponse;
import com.partvision.compras.dto.VincularImportadoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Las lineas IMPORTADOS de las compras: pedidos puntuales que llegan sin codigo. */
@RestController
@RequestMapping("/api/v1/compras/importados")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ImportadosController {

    private final ImportadosService importadosService;

    @GetMapping
    public Page<ImportadoPendienteResponse> pendientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return importadosService.listarPendientes(PageRequest.of(page, size));
    }

    @GetMapping("/sku-sugerido")
    public Map<String, String> skuSugerido() {
        return Map.of("sku", importadosService.proponerSku());
    }

    @PostMapping("/{lineaId}/alta")
    public ResponseEntity<ImportadoResueltoResponse> alta(
            @PathVariable Long lineaId,
            @Valid @RequestBody AltaImportadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importadosService.darDeAlta(lineaId, request));
    }

    @PostMapping("/{lineaId}/vincular")
    public ImportadoResueltoResponse vincular(
            @PathVariable Long lineaId,
            @Valid @RequestBody VincularImportadoRequest request) {
        return importadosService.vincular(lineaId, request);
    }
}
