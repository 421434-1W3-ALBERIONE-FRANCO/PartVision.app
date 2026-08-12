package com.partvision.inventory.controller;

import com.partvision.inventory.dto.AjusteRequest;
import com.partvision.inventory.dto.ConteoLoteRequest;
import com.partvision.inventory.dto.ConteoRequest;
import com.partvision.inventory.dto.ConteoResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /** Conteo físico: fija la cantidad real de un producto en una ubicación. */
    @PostMapping("/conteos")
    public ResponseEntity<ConteoResponse> conteo(@Valid @RequestBody ConteoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.registrarConteo(request));
    }

    /** Conteo físico en lote (carga de existencias reales de varias líneas). */
    @PostMapping("/conteos/lote")
    public ResponseEntity<List<ConteoResponse>> conteoLote(@Valid @RequestBody ConteoLoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.registrarConteoLote(request));
    }

    @GetMapping
    public ResponseEntity<StockResumenResponse> resumen(@RequestParam Long productoId) {
        return ResponseEntity.ok(stockService.getResumen(productoId));
    }

    /** Elimina la existencia de un producto en una ubicación (borra la fila de stock). */
    @DeleteMapping
    public ResponseEntity<Void> eliminar(@RequestParam Long productoId, @RequestParam Long ubicacionId) {
        stockService.eliminarStock(productoId, ubicacionId);
        return ResponseEntity.noContent().build();
    }
}
