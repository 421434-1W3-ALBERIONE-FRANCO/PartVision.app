package com.partvision.compras.dto;

import com.partvision.compras.domain.Compra;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CompraResponse(
        Long id,
        String numeroFactura,
        LocalDate fechaFactura,
        String proveedor,
        String estado,
        Long ubicacionIngresoId,
        String ubicacionIngresoCodigo,
        int totalLineas,
        int totalUnidades,
        int lineasMatcheadas,
        Instant createdAt,
        List<CompraLineaResponse> lineas
) {
    public static CompraResponse from(Compra c, boolean incluirLineas) {
        return from(c, incluirLineas, Map.of());
    }

    public static CompraResponse from(Compra c, boolean incluirLineas,
                                       Map<Long, UbicacionSugerida> stockSugerido) {
        List<CompraLineaResponse> lineasDto = incluirLineas
                ? c.getLineas().stream().map(l -> {
                    UbicacionSugerida sug = l.getProducto() != null
                            ? stockSugerido.get(l.getProducto().getId()) : null;
                    return sug != null
                            ? CompraLineaResponse.from(l, sug.id(), sug.codigo())
                            : CompraLineaResponse.from(l);
                }).toList()
                : List.of();

        int matcheadas = (int) c.getLineas().stream().filter(l -> l.getProducto() != null).count();
        int unidades = c.getLineas().stream().mapToInt(l -> l.getCantidad()).sum();

        return new CompraResponse(
                c.getId(),
                c.getNumeroFactura(),
                c.getFechaFactura(),
                c.getProveedor(),
                c.getEstado().name(),
                c.getUbicacionIngreso() != null ? c.getUbicacionIngreso().getId() : null,
                c.getUbicacionIngreso() != null ? c.getUbicacionIngreso().getCodigo() : null,
                c.getLineas().size(),
                unidades,
                matcheadas,
                c.getCreatedAt(),
                lineasDto
        );
    }

    public record UbicacionSugerida(Long id, String codigo) {}
}
