package com.partvision.inventory.dto;

import com.partvision.inventory.domain.Stock;
import com.partvision.location.domain.Ubicacion;

import java.util.List;

/**
 * Resumen de stock de un producto: total y desglose por ubicacion.
 */
public record StockResumenResponse(Long productoId, int total, List<StockLinea> ubicaciones) {

    public record StockLinea(Long ubicacionId, String ubicacionPath, int cantidad) {

        public static StockLinea from(Stock stock) {
            Ubicacion u = stock.getUbicacion();
            return new StockLinea(u.getId(), u.getPath(), stock.getCantidad());
        }
    }

    public static StockResumenResponse from(Long productoId, List<Stock> stocks) {
        List<StockLinea> lineas = stocks.stream().map(StockLinea::from).toList();
        int total = stocks.stream().mapToInt(Stock::getCantidad).sum();
        return new StockResumenResponse(productoId, total, lineas);
    }
}
