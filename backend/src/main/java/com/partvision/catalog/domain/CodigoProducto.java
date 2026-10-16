package com.partvision.catalog.domain;

import java.util.Locale;

/**
 * La "clave" de un codigo de producto: en mayusculas y sin espacios, puntos, guiones ni barras.
 * Sirve para reconocer el mismo codigo escrito de otra forma: el 2026-10-15 EGSA paso a mandar
 * 651 codigos de PACKSON con un guion de mas ({@code PK-2000} por {@code PK2000}).
 *
 * <p>Ojo: dos codigos con la misma clave NO son siempre el mismo producto. En el catalogo de EGSA
 * hay 156 claves compartidas por productos distintos ({@code PK3000} es SABI y {@code PK-3000}
 * es PACKSON). Por eso la clave solo se usa como ultimo recurso y con controles: mismo
 * proveedor, un solo candidato y la misma marca. Tiene que coincidir con la expresion del indice
 * {@code ix_productos_clave_sku} (V34) y con la consulta de
 * {@code ProductoRepository.findByClaveSkuIn}.
 */
public final class CodigoProducto {

    private CodigoProducto() {
    }

    public static String clave(String codigo) {
        if (codigo == null) {
            return null;
        }
        return codigo.toUpperCase(Locale.ROOT).replaceAll("[\\s./-]", "");
    }
}
