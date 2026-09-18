package com.partvision.compras.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Las filas de la planilla. Acepta {@code value}, que es como Power Automate entrega el
 * resultado de "Enumerar las filas de una tabla": el flujo puede mandar ese cuerpo tal cual.
 */
public record RecepcionFilasRequest(
        @JsonAlias({"value"}) @NotNull @Size(max = MAX_FILAS) List<@NotNull FilaSheetRequest> filas
) {
    /**
     * Tope por envio. Una semana de facturas no se acerca; si la planilla nunca se vacia y
     * crece por encima de esto, hay que borrar las filas viejas (ya recibidas).
     */
    public static final int MAX_FILAS = 5_000;
}
