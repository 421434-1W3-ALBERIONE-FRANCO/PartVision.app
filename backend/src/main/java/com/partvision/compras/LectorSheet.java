package com.partvision.compras;

import com.partvision.common.exception.BusinessException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Interpreta los valores de la planilla del cliente tal como los manda Power Automate: todo
 * llega como texto, y la misma celda puede venir en formatos distintos segun como este
 * configurada la accion que lee la tabla.
 */
final class LectorSheet {

    /**
     * Lo que la planilla escribe en la columna Codigo cuando la pieza no tiene codigo de
     * catalogo. Se comparan normalizados (sin tildes, en mayusculas). Ningun producto del
     * catalogo tiene un SKU que empiece con "import", asi que no se pisa nada real.
     */
    private static final Set<String> SIN_CODIGO_PROPIO = Set.of("IMPORTADO", "IMPORTADOS");

    /** Codigo que se asigna a las lineas sin codigo: pedidos puntuales de clientes. */
    static final String CODIGO_IMPORTADO = "IMPORTADOS";

    private static final DateTimeFormatter DIA_MES_ANIO =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Excel cuenta los dias desde el 30/12/1899 (arrastra el bug del bisiesto de 1900). */
    private static final LocalDate EPOCA_EXCEL = LocalDate.of(1899, 12, 30);
    /** 01/01/1990 y 31/12/2099: un numero fuera de ese rango no es una fecha de factura. */
    private static final long SERIE_MIN = 32874;
    private static final long SERIE_MAX = 73050;

    /**
     * Tope de cordura para la cantidad de una linea. No es un limite del negocio: es para
     * detectar una fila mal armada en la planilla. El 2026-09-24 llego una con cantidad
     * 1.197.421 y la descripcion partida en dos lineas ("1.00
04178311 std jgo aros"): se le
     * habia colado el valor de otra columna. Lo mas alto legitimo visto son 240 unidades
     * (retenes PKRV-043), asi que 10.000 deja pasar cualquier compra real.
     */
    static final int CANTIDAD_MAXIMA = 10_000;

    /** Entero positivo, admitiendo el ".0" con el que a veces se serializa un numero. */
    private static final Pattern ENTERO = Pattern.compile("\\d+(\\.0+)?");

    private LectorSheet() {
    }

    /**
     * Acepta {@code 24/08/2026}, {@code 2026-08-24}, {@code 2026-08-24T00:00:00Z} y el numero
     * de serie de Excel ({@code 46258}): Power Automate devuelve este ultimo por defecto al
     * leer una tabla, salvo que se elija el formato ISO 8601 en la accion.
     */
    static LocalDate fecha(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new BusinessException("Falta la fecha de la factura");
        }
        String v = valor.trim();
        try {
            return LocalDate.parse(v, DIA_MES_ANIO);
        } catch (DateTimeParseException ignorada) {
            // no es dd/MM/yyyy: se prueban los otros formatos
        }
        if (v.length() >= 10 && v.charAt(4) == '-') {
            try {
                return LocalDate.parse(v.substring(0, 10));
            } catch (DateTimeParseException ignorada) {
                // no es ISO
            }
        }
        try {
            long dias = (long) Math.floor(Double.parseDouble(v));
            if (dias >= SERIE_MIN && dias <= SERIE_MAX) {
                return EPOCA_EXCEL.plusDays(dias);
            }
        } catch (NumberFormatException ignorada) {
            // no es un numero
        }
        throw new BusinessException("Formato de fecha inválido: " + valor + ". Usar dd/MM/yyyy");
    }

    /**
     * Cantidad entera y positiva, o null si la fila no la tiene: la planilla mezcla
     * anotaciones entre los productos (por ejemplo una fila "CONTROLO:" sin cantidad).
     */
    static Integer cantidad(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        if (!ENTERO.matcher(v).matches()) {
            return null;
        }
        String parteEntera = v.contains(".") ? v.substring(0, v.indexOf('.')) : v;
        try {
            int n = Integer.parseInt(parteEntera);
            return n > 0 ? n : null;
        } catch (NumberFormatException demasiadoGrande) {
            return null;
        }
    }

    /**
     * La planilla usa "EN TRÁNSITO" e "INGRESADA". Solo INGRESADA mueve la compra: cualquier
     * otro valor, vacio incluido, la deja en transito, que es lo que no toca el stock.
     */
    static boolean dicenIngresada(String estatus) {
        return estatus != null && normalizar(estatus).contains("INGRESAD");
    }

    /**
     * El codigo de catalogo de la linea, o {@link #CODIGO_IMPORTADO} si no tiene uno propio.
     *
     * <p>La celda vacia no es la unica forma de decirlo: en el primer envio real de la planilla
     * (2026-09-24), 76 de 228 lineas traian escrita la palabra "Importado" en la columna Codigo.
     * Tomada al pie de la letra es un codigo que no existe en el catalogo, asi que esas lineas
     * quedaban sin producto y fuera del boton Importados, que es justo donde tienen que estar.
     */
    static String codigo(String valor) {
        if (valor == null || valor.isBlank()) {
            return CODIGO_IMPORTADO;
        }
        String limpio = valor.trim();
        return SIN_CODIGO_PROPIO.contains(normalizar(limpio)) ? CODIGO_IMPORTADO : limpio;
    }

    /** Texto recortado, o null si no hay nada. */
    static String limpio(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Sin tildes, espacios de mas ni diferencias de mayusculas: para comparar nombres. */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
