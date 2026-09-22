package com.partvision.compras.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Las filas de la planilla. Segun como se arme la accion HTTP, Power Automate manda el cuerpo
 * de tres formas, y las tres entran:
 *
 * <ul>
 *   <li>{@code {"value": [...]}}: la salida completa de "Enumerar las filas de una tabla".</li>
 *   <li>{@code [...]}: solo el array, que es lo que queda al insertar el contenido dinamico.</li>
 *   <li>{@code {"filas": [...]}}: el nombre propio, para quien arme el JSON a mano.</li>
 * </ul>
 */
public record RecepcionFilasRequest(
        @NotNull @Size(max = MAX_FILAS) List<@NotNull FilaSheetRequest> filas
) {
    /**
     * Tope por envio. Una semana de facturas no se acerca; si la planilla nunca se vacia y
     * crece por encima de esto, hay que borrar las filas viejas (ya recibidas).
     */
    public static final int MAX_FILAS = 5_000;

    /**
     * Tiene que ignorar lo que no conoce: Power Automate agrega {@code @odata.etag} e
     * {@code ItemInternalId} a cada fila. Un ObjectMapper recien creado, al contrario del que
     * arma Spring, falla ante un campo de mas.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private static final TypeReference<List<FilaSheetRequest>> TIPO_FILAS = new TypeReference<>() {
    };

    @JsonCreator
    static RecepcionFilasRequest desde(JsonNode cuerpo) {
        JsonNode filas = cuerpo;
        if (!cuerpo.isArray()) {
            filas = cuerpo.has("value") ? cuerpo.get("value") : cuerpo.get("filas");
        }
        if (filas == null || !filas.isArray()) {
            throw new IllegalArgumentException("Se esperaba el array de filas de la planilla");
        }
        return new RecepcionFilasRequest(MAPPER.convertValue(filas, TIPO_FILAS));
    }
}
