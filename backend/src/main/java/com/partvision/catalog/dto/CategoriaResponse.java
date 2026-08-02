package com.partvision.catalog.dto;

import com.partvision.catalog.domain.Categoria;

public record CategoriaResponse(Long id, String nombre, Long parentId, String parentNombre) {

    public static CategoriaResponse from(Categoria categoria) {
        Categoria parent = categoria.getParent();
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNombre(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getNombre());
    }
}
