package com.partvision.compras.dto;

import com.partvision.compras.domain.CompraLinea;

import java.time.LocalDate;

/**
 * Una linea que llego sin codigo (pedido puntual de un cliente) y todavia no esta asociada a
 * ningun producto del catalogo. {@code estadoCompra} decide si al resolverla se carga el stock
 * en el momento (INGRESADA) o cuando se ingrese la compra.
 */
public record ImportadoPendienteResponse(
        Long lineaId,
        Long compraId,
        String factura,
        LocalDate fechaFactura,
        String proveedor,
        String estadoCompra,
        String descripcion,
        int cantidad
) {
    public static ImportadoPendienteResponse from(CompraLinea linea) {
        return new ImportadoPendienteResponse(
                linea.getId(),
                linea.getCompra().getId(),
                linea.getCompra().getNumeroFactura(),
                linea.getCompra().getFechaFactura(),
                linea.getCompra().getProveedor(),
                linea.getCompra().getEstado().name(),
                linea.getDescripcion(),
                linea.getCantidad());
    }
}
