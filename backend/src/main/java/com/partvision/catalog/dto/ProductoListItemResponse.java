package com.partvision.catalog.dto;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista resumida para listados. Incluye el stock (total y por ubicación) para el
 * control real de inventario en la pantalla de catálogo.
 */
public record ProductoListItemResponse(
        Long id,
        String sku,
        String descripcion,
        ProductoEstado estado,
        String marcaNombre,
        String categoriaNombre,
        int stockTotal,
        List<StockUbicacion> ubicaciones,
        BigDecimal precioCosto,
        BigDecimal precioVenta
) {
    /** Cantidad de un producto en una ubicación (por su código, sin IDs). */
    public record StockUbicacion(String codigo, int cantidad) {
    }

    /** Compat: sin datos de stock (total 0, sin ubicaciones). */
    public ProductoListItemResponse(Long id, String sku, String descripcion, ProductoEstado estado,
                                    String marcaNombre, String categoriaNombre) {
        this(id, sku, descripcion, estado, marcaNombre, categoriaNombre, 0, List.of(), null, null);
    }

    public static ProductoListItemResponse from(Producto producto) {
        Marca marca = producto.getMarca();
        Categoria categoria = producto.getCategoria();
        return new ProductoListItemResponse(
                producto.getId(),
                producto.getSku(),
                producto.getDescripcion(),
                producto.getEstado(),
                marca == null ? null : marca.getNombre(),
                categoria == null ? null : categoria.getNombre());
    }

    public static ProductoListItemResponse from(Producto producto, int stockTotal, List<StockUbicacion> ubicaciones) {
        Marca marca = producto.getMarca();
        Categoria categoria = producto.getCategoria();
        return new ProductoListItemResponse(
                producto.getId(),
                producto.getSku(),
                producto.getDescripcion(),
                producto.getEstado(),
                marca == null ? null : marca.getNombre(),
                categoria == null ? null : categoria.getNombre(),
                stockTotal,
                ubicaciones,
                producto.getPrecioCosto(),
                producto.getPrecioVenta());
    }
}
