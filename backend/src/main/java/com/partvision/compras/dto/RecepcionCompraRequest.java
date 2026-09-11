package com.partvision.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecepcionCompraRequest(
        @NotBlank @Size(max = 50) String factura,
        @NotBlank String fechaFactura,
        String proveedor,
        @NotBlank String estatus,
        @NotEmpty @Size(max = MAX_LINEAS) @Valid List<RecepcionLineaRequest> lineas
) {
    /**
     * Tope de lineas por factura. La recepcion es publica, asi que sin limite el tamano del
     * request lo decide quien llama: ademas del riesgo de memoria, un IN gigante contra el
     * catalogo choca con el maximo de 65.535 parametros del driver JDBC.
     */
    public static final int MAX_LINEAS = 5_000;
}
