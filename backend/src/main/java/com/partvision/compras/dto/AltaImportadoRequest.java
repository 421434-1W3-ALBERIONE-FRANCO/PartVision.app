package com.partvision.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dar de alta en el catalogo la pieza de una linea importada. {@code ubicacionId} solo hace
 * falta si la compra ya ingreso: ahi el stock se carga en el momento. Si la compra todavia no
 * ingreso, el stock se carga al ingresarla, con la ubicacion que se elija entonces.
 */
public record AltaImportadoRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 500) String descripcion,
        Long ubicacionId
) {
}
