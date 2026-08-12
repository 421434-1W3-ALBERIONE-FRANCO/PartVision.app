package com.partvision.location.dto;

import com.partvision.location.domain.TipoUbicacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta/edición de una ubicación física. El cliente trabaja con SU código
 * (la nomenclatura que ya tiene arraigada). {@code tipo} y {@code parentId} son
 * opcionales (modelo plano por defecto); {@code descripcion} es una nota libre
 * opcional (color, referencia, etc.).
 */
public record UbicacionRequest(
        TipoUbicacion tipo,
        @NotBlank @Size(max = 50) String codigo,
        @Size(max = 300) String descripcion,
        Long parentId
) {
    /** Constructor de compatibilidad (sin descripción). */
    public UbicacionRequest(TipoUbicacion tipo, String codigo, Long parentId) {
        this(tipo, codigo, null, parentId);
    }
}
