package com.partvision.compras.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Una fila de la tabla "Ingreso stock" del cliente, con los nombres de columna tal como estan
 * en la planilla: el flujo de Power Automate puede mandar las filas sin transformarlas. Todo
 * llega como texto; lo interpreta {@code LectorSheet}. Campos que la planilla agregue de mas
 * (y los que agrega Power Automate, como {@code @odata.etag}) se ignoran.
 */
public record FilaSheetRequest(
        @JsonAlias({"Factura"}) String factura,
        @JsonAlias({"F. Factura", "Fecha", "Fecha Factura"}) String fechaFactura,
        @JsonAlias({"Codigo", "Código"}) String codigo,
        @JsonAlias({"Cantidad"}) String cantidad,
        @JsonAlias({"Descripcion", "Descripción"}) String descripcion,
        @JsonAlias({"Estatus stock", "Estatus", "Estado"}) String estatus,
        @JsonAlias({"Proveedor"}) String proveedor
) {
}
