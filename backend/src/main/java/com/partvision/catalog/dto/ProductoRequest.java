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
 *
 * La marca puede indicarse por {@code marcaId} (existente) o por {@code marcaNombre}
 * (texto libre, ej: el que detecto la IA): en ese caso el catalogo la resuelve o la
 * crea. Si vienen ambos, manda el id.
 */
public record ProductoRequest(
        @Size(max = 100) String sku,
        Long marcaId,
        @Size(max = 150) String marcaNombre,
        Long categoriaId,
        @NotBlank @Size(max = 500) String descripcion,
        ProductoEstado estado,
        Map<String, Object> detallesExtra,
        @Valid List<ProductoCodigoRequest> codigos,
        @Size(max = 150) String proveedor
) {
}
