package com.partvision.pricing.domain;

public enum OrigenSincronizacion {
    /** La corrida programada del servidor. */
    AUTOMATICA,
    /** El boton "Actualizar ahora" de la pantalla Precios (o "Aplicar igual" a una lista retenida). */
    MANUAL,
    /** La lista que mando el robot del cliente por la API (EGSA). */
    RECEPCION
}
