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
        @NotEmpty @Valid List<RecepcionLineaRequest> lineas
) {}
