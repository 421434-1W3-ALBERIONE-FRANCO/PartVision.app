package com.partvision.catalog.dto;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;

/**
 * Vista resumida para listados (sin codigos ni detalles_extra).
 */
public record ProductoListItemResponse(
        Long id,
        String sku,
        String descripcion,
        ProductoEstado estado,
        String marcaNombre,
        String categoriaNombre
) {
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
}
