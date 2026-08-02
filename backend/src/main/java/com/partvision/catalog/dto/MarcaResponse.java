package com.partvision.catalog.dto;

import com.partvision.catalog.domain.Marca;

public record MarcaResponse(Long id, String nombre) {

    public static MarcaResponse from(Marca marca) {
        return new MarcaResponse(marca.getId(), marca.getNombre());
    }
}
