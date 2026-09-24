package com.partvision.compras.dto;

import com.partvision.catalog.domain.Producto;
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
        int cantidad,
        Sugerencia sugerencia
) {
    /**
     * Un producto del catalogo que podria ser esta pieza, deducido del codigo que la planilla
     * escribe al principio de la descripcion ("bie0199 biela"). Es una sugerencia y nada mas:
     * se muestra para que una persona decida, no se asocia sola. Con ~10.300 SKU repetidos uno
     * por proveedor, el mismo codigo devuelve dos productos distintos, y acertarle al que no es
     * carga el stock en el producto equivocado.
     */
    public record Sugerencia(
            Long productoId,
            String sku,
            String descripcion,
            String proveedor,
            /** El proveedor del producto coincide con el de la factura. Si no, ojo. */
            boolean mismoProveedor
    ) {
        public static Sugerencia from(Producto producto, String proveedorFactura) {
            return new Sugerencia(
                    producto.getId(),
                    producto.getSku(),
                    producto.getDescripcion(),
                    producto.getProveedor(),
                    producto.getProveedor() != null
                            && producto.getProveedor().equalsIgnoreCase(proveedorFactura));
        }
    }

    public static ImportadoPendienteResponse from(CompraLinea linea) {
        return from(linea, null);
    }

    public static ImportadoPendienteResponse from(CompraLinea linea, Sugerencia sugerencia) {
        return new ImportadoPendienteResponse(
                linea.getId(),
                linea.getCompra().getId(),
                linea.getCompra().getNumeroFactura(),
                linea.getCompra().getFechaFactura(),
                linea.getCompra().getProveedor(),
                linea.getCompra().getEstado().name(),
                linea.getDescripcion(),
                linea.getCantidad(),
                sugerencia);
    }
}
