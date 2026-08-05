package com.partvision.catalog.dto;

import com.partvision.catalog.domain.ProductoEstado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Alta / edicion de un producto del catalogo.
 * El SKU y los codigos son opcionales (hay productos sin codigo).
 */
public record ProductoRequest(
        @Size(max = 100) String sku,
        Long marcaId,
        Long categoriaId,
        @NotBlank @Size(max = 500) String descripcion,
        ProductoEstado estado,
        Map<String, Object> detallesExtra,
        @Valid List<ProductoCodigoRequest> codigos,
        @Size(max = 150) String proveedor
) {
}
