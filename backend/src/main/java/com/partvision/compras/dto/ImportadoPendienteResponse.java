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
     * Un producto del catalogo que podria ser esta pieza. Es una sugerencia y nada mas: se
     * muestra para que una persona decida. Con ~10.300 SKU repetidos uno por proveedor, el
     * mismo codigo devuelve dos productos distintos, y acertarle al que no es carga el stock en
     * el producto equivocado. (Cuando el codigo de la descripcion es del MISMO proveedor de la
     * factura no hay duda, y la linea ya se asocia sola al llegar: no aparece aca.)
     */
    public record Sugerencia(
            Long productoId,
            String sku,
            String descripcion,
            String proveedor,
            /** El proveedor del producto coincide con el de la factura. Si no, ojo. */
            boolean mismoProveedor,
            /**
             * CODIGO: el codigo que el cliente anoto al principio de la descripcion ("bie0199
             * biela") existe en el catalogo. DESCRIPCION: no hay codigo, pero la descripcion se
             * parece mucho a la de este producto.
             */
            String motivo,
            /** Cuanto se parecen las descripciones, de 0 a 100; null si vino por el codigo. */
            Integer parecido
    ) {
        public static final String POR_CODIGO = "CODIGO";
        public static final String POR_DESCRIPCION = "DESCRIPCION";

        public static Sugerencia from(Producto producto, String proveedorFactura) {
            return new Sugerencia(producto.getId(), producto.getSku(), producto.getDescripcion(),
                    producto.getProveedor(), mismoProveedor(producto, proveedorFactura), POR_CODIGO, null);
        }

        public static Sugerencia porDescripcion(Producto producto, String proveedorFactura, double parecido) {
            return new Sugerencia(producto.getId(), producto.getSku(), producto.getDescripcion(),
                    producto.getProveedor(), mismoProveedor(producto, proveedorFactura), POR_DESCRIPCION,
                    (int) Math.round(parecido * 100));
        }

        private static boolean mismoProveedor(Producto producto, String proveedorFactura) {
            return producto.getProveedor() != null && producto.getProveedor().equalsIgnoreCase(proveedorFactura);
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
