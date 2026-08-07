package com.partvision.ai.dto;

/**
 * Producto del catalogo que se parece a la extraccion analizada pero no matchea con
 * certeza. Se muestra al revisor para que decida si es el mismo (y evite un duplicado)
 * o si realmente esta cargando uno nuevo. Se incluye la marca y el proveedor porque
 * son justamente los datos que distinguen variantes que difieren en un detalle.
 */
public record CandidatoCoincidencia(
        Long id,
        String sku,
        String descripcion,
        String marca,
        String proveedor
) {
}
