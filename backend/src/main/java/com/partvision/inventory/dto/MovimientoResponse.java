package com.partvision.inventory.dto;

import com.partvision.inventory.domain.MovimientoStock;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.location.domain.Ubicacion;

import java.time.Instant;

public record MovimientoResponse(
        Long id,
        Long productoId,
        TipoMovimiento tipo,
        int cantidad,
        Long ubicacionOrigenId,
        Long ubicacionDestinoId,
        Long usuarioId,
        String motivo,
        String referencia,
        Instant fecha
) {
    public static MovimientoResponse from(MovimientoStock m) {
        Ubicacion origen = m.getUbicacionOrigen();
        Ubicacion destino = m.getUbicacionDestino();
        return new MovimientoResponse(
                m.getId(),
                m.getProducto().getId(),
                m.getTipo(),
                m.getCantidad(),
                origen == null ? null : origen.getId(),
                destino == null ? null : destino.getId(),
                m.getCreatedBy(),
                m.getMotivo(),
                m.getReferencia(),
                m.getCreatedAt());
    }
}
