package com.partvision.inventory.dto;

import com.partvision.inventory.domain.TipoMovimiento;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ajuste de inventario. El tipo debe ser AJUSTE_POSITIVO o AJUSTE_NEGATIVO.
 * El motivo es obligatorio para dejar trazabilidad de por que se ajusto.
 */
public record AjusteRequest(
        @NotNull Long productoId,
        @NotNull Long ubicacionId,
        @NotNull TipoMovimiento tipo,
        @NotNull @Min(1) Integer cantidad,
        @NotBlank @Size(max = 300) String motivo
) {
}
