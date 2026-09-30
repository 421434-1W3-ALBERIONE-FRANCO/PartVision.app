package com.partvision.compras.dto;

import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraLinea;

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
        String estadoPlanilla,
        Long ubicacionIngresoId,
        String ubicacionIngresoCodigo,
        int totalLineas,
        int totalUnidades,
        int lineasMatcheadas,
        Instant createdAt,
        List<CompraLineaResponse> lineas,
        /**
         * Las lineas con una cantidad fuera de lo normal que nadie reviso todavia. Van siempre,
         * tambien en el listado: el panel las muestra en la misma fila de la factura, para
         * aceptarlas o descartarlas sin abrir la compra.
         */
        List<LineaEnRevision> lineasEnRevision,
        /** Lineas que ya entraron al stock. */
        int lineasEnStock,
        /**
         * Lineas con articulo que todavia no entraron. Mientras sea mayor que cero, la compra no
         * esta ingresada aunque tenga stock cargado: se ingresa por partes.
         */
        int lineasPorUbicar,
        /** Lo que de verdad esta en el stock. Puede ser menos que totalUnidades. */
        int unidadesEnStock
) {
    public record LineaEnRevision(Long id, String codigo, String descripcion, int cantidad) {
        static LineaEnRevision from(CompraLinea l) {
            return new LineaEnRevision(l.getId(), l.getCodigo(), l.getDescripcion(), l.getCantidad());
        }
    }

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
        // Una linea descartada es un error de la planilla: no cuenta en los totales.
        int unidades = c.getLineas().stream()
                .filter(l -> !l.descartada())
                .mapToInt(CompraLinea::getCantidad)
                .sum();
        List<LineaEnRevision> enRevision = c.getLineas().stream()
                .filter(CompraLinea::pendienteDeRevision)
                .map(LineaEnRevision::from)
                .toList();

        return new CompraResponse(
                c.getId(),
                c.getNumeroFactura(),
                c.getFechaFactura(),
                c.getProveedor(),
                c.getEstado().name(),
                c.getEstadoPlanilla().name(),
                c.getUbicacionIngreso() != null ? c.getUbicacionIngreso().getId() : null,
                c.getUbicacionIngreso() != null ? c.getUbicacionIngreso().getCodigo() : null,
                c.getLineas().size(),
                unidades,
                matcheadas,
                c.getCreatedAt(),
                lineasDto,
                enRevision,
                (int) c.getLineas().stream().filter(CompraLinea::enStock).count(),
                (int) c.lineasPorUbicar(),
                c.getLineas().stream().filter(CompraLinea::enStock).mapToInt(CompraLinea::getCantidad).sum()
        );
    }

    public record UbicacionSugerida(Long id, String codigo) {}
}
