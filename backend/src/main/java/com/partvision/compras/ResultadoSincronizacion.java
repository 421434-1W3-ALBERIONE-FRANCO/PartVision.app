package com.partvision.compras;

import com.partvision.compras.domain.Compra;

/**
 * Que paso con una factura al recibirla. Los conteos de lineas se calculan dentro de la
 * transaccion, para no depender de que la coleccion este cargada despues.
 */
public record ResultadoSincronizacion(
        Tipo tipo,
        Compra compra,
        int lineas,
        int lineasMatcheadas,
        String mensaje
) {
    public enum Tipo {
        /** No existia: se registro. */
        CREADA,
        /** Existia con el mismo contenido y cambio algo que manda la planilla (estado, proveedor). */
        ACTUALIZADA,
        /** Existia igual: un reenvio. */
        SIN_CAMBIOS,
        /** Existia con otro contenido, o la planilla pide algo que ya no se puede deshacer. */
        CONFLICTO
    }
}
