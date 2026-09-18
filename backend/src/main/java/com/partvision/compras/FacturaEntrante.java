package com.partvision.compras;

import com.partvision.compras.domain.CompraEstado;

import java.time.LocalDate;
import java.util.List;

/**
 * Una factura ya leida y validada, venga del endpoint de una factura o de las filas de la
 * planilla. {@code estado} es lo que dice la planilla: EN_TRANSITO o POR_UBICAR.
 */
public record FacturaEntrante(
        String numero,
        LocalDate fecha,
        String proveedor,
        CompraEstado estado,
        List<Linea> lineas
) {
    public record Linea(String codigo, String descripcion, int cantidad) {
    }
}
