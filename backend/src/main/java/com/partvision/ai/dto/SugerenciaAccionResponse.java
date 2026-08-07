package com.partvision.ai.dto;

import java.util.List;

/**
 * Resultado de analizar una extraccion contra el catalogo: que conviene hacer con ella.
 *
 * @param accion                    NUEVO / YA_EXISTE / AGREGAR_CODIGO / POSIBLES_COINCIDENCIAS
 * @param productoExistenteId       producto que matcheo con certeza (null si NUEVO o POSIBLES_COINCIDENCIAS)
 * @param productoExistenteDescripcion  descripcion del match, para mostrar (null si no hay match unico)
 * @param codigoBarras              codigo de barras detectado (para AGREGAR_CODIGO; puede ser null)
 * @param mensaje                   leyenda lista para mostrar al revisor
 * @param candidatos                productos parecidos a revisar (vacio salvo POSIBLES_COINCIDENCIAS)
 */
public record SugerenciaAccionResponse(
        AccionSugerida accion,
        Long productoExistenteId,
        String productoExistenteDescripcion,
        String codigoBarras,
        String mensaje,
        List<CandidatoCoincidencia> candidatos
) {
}
