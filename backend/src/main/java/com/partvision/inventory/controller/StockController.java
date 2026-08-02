package com.partvision.inventory.controller;

import com.partvision.inventory.dto.AjusteRequest;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.dto.SalidaRequest;
import com.partvision.inventory.dto.StockResumenResponse;
import com.partvision.inventory.dto.TransferenciaRequest;
import com.partvision.inventory.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/entradas")
    public ResponseEntity<MovimientoResponse> entrada(@Valid @RequestBody EntradaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.registrarEntrada(request));
    }

    @PostMapping("/salidas")
    public ResponseEntity<MovimientoResponse> salida(@Valid @RequestBody SalidaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.registrarSalida(request));
    }

    @PostMapping("/ajustes")
    public ResponseEntity<MovimientoResponse> ajuste(@Valid @RequestBody AjusteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.ajustar(request));
    }

    @PostMapping("/transferencias")
    public ResponseEntity<MovimientoResponse> transferencia(@Valid @RequestBody TransferenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.transferir(request));
    }

    @GetMapping
    public ResponseEntity<StockResumenResponse> resumen(@RequestParam Long productoId) {
        return ResponseEntity.ok(stockService.getResumen(productoId));
    }
}
