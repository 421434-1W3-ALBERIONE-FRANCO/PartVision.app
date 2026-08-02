package com.partvision.ai.vision;

import java.util.Map;

/**
 * Resultado estructurado de la extraccion por IA. Cualquier campo no detectado
 * debe venir en null: la IA NO inventa datos ni deduce compatibilidad vehicular.
 *
 * @param codigoPieza   SKU / codigo de pieza detectado (o null)
 * @param marca         marca detectada (o null)
 * @param descripcion   descripcion detectada (o null)
 * @param codigoBarras  codigo de barras detectado (o null)
 * @param detallesExtra atributos sueltos detectados (nunca null; vacio si nada)
 * @param modelo        identificador del modelo/IA que produjo el resultado
 */
public record ExtraccionIA(
        String codigoPieza,
        String marca,
        String descripcion,
        String codigoBarras,
        Map<String, Object> detallesExtra,
        String modelo
) {
}
