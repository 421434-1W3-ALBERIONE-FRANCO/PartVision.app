package com.partvision.catalog.controller;

import com.partvision.catalog.dto.ProductoCodigoRequest;
import com.partvision.catalog.dto.ProductoListItemResponse;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping
    public ResponseEntity<ProductoResponse> create(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> update(
            @PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.update(id, request));
    }

    /** Asocia un codigo (ej: codigo de barras escaneado) a un producto existente. */
    @PostMapping("/{id}/codigos")
    public ResponseEntity<ProductoResponse> agregarCodigo(
            @PathVariable Long id, @Valid @RequestBody ProductoCodigoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.agregarCodigo(id, request));
    }

    @GetMapping
    public ResponseEntity<Page<ProductoListItemResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(productoService.findAll(pageable));
    }

    /** Busqueda por codigo (flujo de escaneo). 404 si no existe. */
    @GetMapping("/buscar")
    public ResponseEntity<ProductoResponse> buscarPorCodigo(@RequestParam String codigo) {
        return ResponseEntity.ok(productoService.buscarPorCodigo(codigo));
    }

    /** Busqueda por texto parcial (descripcion, SKU, marca, categoria). Paginada. */
    @GetMapping("/buscar-texto")
    public ResponseEntity<Page<ProductoListItemResponse>> buscarPorTexto(
            @RequestParam String q, Pageable pageable) {
        return ResponseEntity.ok(productoService.buscarPorTexto(q, pageable));
    }
}
