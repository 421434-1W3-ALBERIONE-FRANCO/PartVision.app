package com.partvision.location.dto;

import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.domain.Ubicacion;

public record UbicacionResponse(
        Long id,
        TipoUbicacion tipo,
        String codigo,
        String descripcion,
        String path,
        boolean activo,
        Long parentId
) {
    public static UbicacionResponse from(Ubicacion ubicacion) {
        Ubicacion parent = ubicacion.getParent();
        return new UbicacionResponse(
                ubicacion.getId(),
                ubicacion.getTipo(),
                ubicacion.getCodigo(),
                ubicacion.getDescripcion(),
                ubicacion.getPath(),
                ubicacion.isActivo(),
                parent == null ? null : parent.getId());
    }
}
