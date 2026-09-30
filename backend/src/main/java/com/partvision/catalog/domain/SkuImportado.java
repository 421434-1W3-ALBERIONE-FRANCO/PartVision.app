package com.partvision.catalog.domain;

import java.util.Locale;

/**
 * Los codigos {@code IMP-NNNNN} de las piezas importadas que se dan de alta en el catalogo.
 *
 * <p>Los asigna el sistema, de una secuencia de la base, y ninguna persona puede escribirlos
 * ni cambiarlos: el prefijo esta reservado. Por eso vive en el catalogo y no en compras: es el
 * alta de productos la que tiene que rechazarlo cuando viene de cualquier otro lado.
 */
public final class SkuImportado {

    /** Ningun SKU de proveedor empieza asi (verificado sobre el catalogo al reservarlo). */
    public static final String PREFIJO = "IMP-";

    private SkuImportado() {
    }

    public static boolean es(String sku) {
        return sku != null && sku.trim().toUpperCase(Locale.ROOT).startsWith(PREFIJO);
    }

    public static String formatear(long numero) {
        return String.format(Locale.ROOT, "%s%05d", PREFIJO, numero);
    }
}
