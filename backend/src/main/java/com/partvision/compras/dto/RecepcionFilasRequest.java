package com.partvision.compras.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return new RecepcionFilasRequest(MAPPER.convertValue(conNombresReales(filas), TIPO_FILAS));
    }

    /**
     * Devuelve las filas con los nombres de columna como estan en la planilla. El conector de
     * Excel escapa los caracteres que no puede usar en un identificador: la columna "F. Factura"
     * llega como {@code F_x002e_ Factura}, y una columna con acento, como {@code Descripci_x00f3_n}.
     *
     * <p>Sin deshacer eso, la columna no coincide con ningun alias y se ignora: el 2026-09-24 la
     * fecha de todas las facturas quedaba en null y nada fallaba. Se deshace aca, para toda
     * columna, porque los nombres los pone el cliente en su planilla y pueden cambiar.
     */
    private static JsonNode conNombresReales(JsonNode filas) {
        ArrayNode limpias = MAPPER.createArrayNode();
        for (JsonNode fila : filas) {
            if (!fila.isObject()) {
                limpias.add(fila);
                continue;
            }
            ObjectNode destino = MAPPER.createObjectNode();
            fila.fields().forEachRemaining(campo -> destino.set(desescapar(campo.getKey()), campo.getValue()));
            limpias.add(destino);
        }
        return limpias;
    }

    private static final Pattern ESCAPE_EXCEL = Pattern.compile("_x([0-9A-Fa-f]{4})_");

    private static String desescapar(String nombre) {
        Matcher escapes = ESCAPE_EXCEL.matcher(nombre);
        if (!escapes.find()) {
            return nombre;
        }
        StringBuilder real = new StringBuilder();
        escapes.reset();
        while (escapes.find()) {
            escapes.appendReplacement(real, Matcher.quoteReplacement(
                    String.valueOf((char) Integer.parseInt(escapes.group(1), 16))));
        }
        escapes.appendTail(real);
        return real.toString();
    }
}
