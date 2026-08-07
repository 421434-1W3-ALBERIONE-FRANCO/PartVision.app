package com.partvision.catalog.service;

import org.springframework.stereotype.Component;

/**
 * Normalizacion y comparacion de codigos de pieza / SKU para la deteccion de duplicados.
 *
 * <p>Quita el ruido de formato (espacios, mayusculas/minusculas y separadores como
 * parentesis, guiones, "+", ".") pero PRESERVA los digitos y letras que distinguen la
 * medida y la variante. Por eso dos codigos que solo cambian el formato normalizan igual,
 * mientras que dos que cambian la medida quedan distintos:
 *
 * <ul>
 *   <li>{@code "81 3667+0.5"} y {@code "813667(05)"} -> {@code "81366705"} (mismo producto)</li>
 *   <li>{@code "813667(STD)"} -> {@code "813667STD"} (variante STD: NO colapsa con la +0.5)</li>
 *   <li>{@code "JAVAEB0*K754"} -> {@code "JAVAEB0K754"} (mismo que el del catalogo)</li>
 * </ul>
 */
@Component
public class ProductoMatcher {

    /** Deja solo alfanumericos en mayuscula. null o vacio -> "". */
    public String normalizar(String codigo) {
        if (codigo == null) {
            return "";
        }
        return codigo.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** Dos codigos son el mismo producto si normalizan igual (y no quedan vacios). */
    public boolean coincideExacto(String a, String b) {
        String na = normalizar(a);
        return !na.isEmpty() && na.equals(normalizar(b));
    }

    /**
     * Ancla para el recall en la BD (prefijo de un LIKE): los digitos iniciales si el
     * codigo arranca con numero, o la corrida inicial de alfanumericos hasta el primer
     * separador si arranca con letra. Es deliberadamente amplia (trae candidatos de mas);
     * el filtro fino lo hace {@link #coincideExacto}.
     */
    public String anclaBusqueda(String codigo) {
        if (codigo == null) {
            return "";
        }
        String s = codigo.toUpperCase().replaceAll("\\s", "");
        if (s.isEmpty()) {
            return "";
        }
        boolean numerico = Character.isDigit(s.charAt(0));
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (!Character.isLetterOrDigit(c)) {
                break;
            }
            if (numerico && !Character.isDigit(c)) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Marcas compatibles salvo que ambas esten presentes y difieran: no se cruza un
     * producto de una marca con el de otra. Si alguna es nula/vacia (el catalogo importado
     * suele venir sin marca), se consideran compatibles.
     */
    public boolean marcaCompatible(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return true;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
