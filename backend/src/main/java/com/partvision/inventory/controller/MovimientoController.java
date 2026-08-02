package com.partvision.inventory.controller;

import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final StockService stockService;

    /** Historial de movimientos de un producto (mas recientes primero). */
    @GetMapping
    public ResponseEntity<Page<MovimientoResponse>> historial(@RequestParam Long productoId, Pageable pageable) {
        return ResponseEntity.ok(stockService.listarMovimientos(productoId, pageable));
    }
}
