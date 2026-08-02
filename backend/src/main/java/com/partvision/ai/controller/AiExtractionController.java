package com.partvision.ai.controller;

import com.partvision.ai.domain.EstadoExtraccion;
import com.partvision.ai.dto.AiExtractionResponse;
import com.partvision.ai.dto.ConfirmacionResponse;
import com.partvision.ai.dto.ConfirmarExtraccionRequest;
import com.partvision.ai.service.AiExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/extracciones")
@RequiredArgsConstructor
public class AiExtractionController {

    private final AiExtractionService aiExtractionService;

    /** Sube una imagen, la IA sugiere datos y se crea un borrador PENDIENTE. */
    @PostMapping
    public ResponseEntity<AiExtractionResponse> extraer(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiExtractionService.extraer(archivo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiExtractionResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(aiExtractionService.findById(id));
    }

    @GetMapping
    public ResponseEntity<Page<AiExtractionResponse>> listar(
            @RequestParam(defaultValue = "PENDIENTE") EstadoExtraccion estado, Pageable pageable) {
        return ResponseEntity.ok(aiExtractionService.listarPorEstado(estado, pageable));
    }

    /** Revision humana: crea el producto oficial (y opcionalmente stock inicial). */
    @PostMapping("/{id}/confirmar")
    public ResponseEntity<ConfirmacionResponse> confirmar(@PathVariable Long id,
                                                          @Valid @RequestBody ConfirmarExtraccionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiExtractionService.confirmar(id, request));
    }

    @PostMapping("/{id}/descartar")
    public ResponseEntity<AiExtractionResponse> descartar(@PathVariable Long id) {
        return ResponseEntity.ok(aiExtractionService.descartar(id));
    }
}
