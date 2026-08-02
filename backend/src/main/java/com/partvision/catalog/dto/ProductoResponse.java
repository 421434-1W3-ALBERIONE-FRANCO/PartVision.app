package com.partvision.catalog.dto;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;

import java.util.List;
import java.util.Map;

public record ProductoResponse(
        Long id,
        String sku,
        Long marcaId,
        String marcaNombre,
        Long categoriaId,
        String categoriaNombre,
        String descripcion,
        ProductoEstado estado,
        Map<String, Object> detallesExtra,
        List<ProductoCodigoResponse> codigos
) {
    public static ProductoResponse from(Producto producto) {
        Marca marca = producto.getMarca();
        Categoria categoria = producto.getCategoria();
        List<ProductoCodigoResponse> codigos = producto.getCodigos().stream()
                .map(ProductoCodigoResponse::from)
                .toList();
        return new ProductoResponse(
                producto.getId(),
                producto.getSku(),
                marca == null ? null : marca.getId(),
                marca == null ? null : marca.getNombre(),
                categoria == null ? null : categoria.getId(),
                categoria == null ? null : categoria.getNombre(),
                producto.getDescripcion(),
                producto.getEstado(),
                producto.getDetallesExtra(),
                codigos);
    }
}
