package com.partvision.pricing;

import com.partvision.pricing.AnalizadorListaPrecios.Umbrales;
import com.partvision.pricing.domain.OrigenSincronizacion;

/**
 * Una lista de precios que PartVision mantiene al dia: de que proveedor es y con que reglas se
 * aplica. ADS la baja de su portal y EGSA la manda el robot del cliente, pero una vez que la
 * lista esta leida las dos pasan por el mismo motor.
 *
 * @param nombre         como se nombra en los mensajes ("ADS", "EGSA")
 * @param recibeArchivo  true si nos la mandan (EGSA); false si la vamos a buscar (ADS)
 */
record FuenteLista(String proveedor, String nombre, boolean habilitada, boolean recibeArchivo,
                   Umbrales umbrales, int diasSinActualizar) {

    static FuenteLista de(AdsSyncProperties p) {
        return new FuenteLista(p.proveedor(), "ADS", p.habilitada(), false, Umbrales.de(p), p.diasSinActualizar());
    }

    static FuenteLista de(EgsaRecepcionProperties p) {
        return new FuenteLista(p.proveedor(), "EGSA", p.habilitada(), true, Umbrales.de(p), p.diasSinActualizar());
    }

    /** Codigos nuevos que se dan de alta solos; 0 = nunca (se hace con la importacion manual). */
    int maxAltas() {
        return umbrales.maxAltas();
    }

    /** Lo que dice el lote en el historial de actualizaciones. */
    String etiqueta(OrigenSincronizacion origen) {
        return switch (origen) {
            case AUTOMATICA -> "Actualización automática de " + nombre;
            case MANUAL -> "Actualización de " + nombre + " (botón)";
            case RECEPCION -> "Lista recibida de " + nombre;
        };
    }

    /** Columna "fuente" del lote: la pantalla muestra "API" para todo lo que no es un Excel subido a mano. */
    String fuenteLote(OrigenSincronizacion origen) {
        return origen == OrigenSincronizacion.RECEPCION ? "API_ENVIO" : "API_SYNC";
    }
}
