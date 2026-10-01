package com.partvision.pricing.domain;

public enum ResultadoSincronizacion {
    EN_CURSO,
    /** Se aplicaron precios nuevos. */
    ACTUALIZADA,
    /** La lista llego bien y no cambio ningun precio. */
    SIN_CAMBIOS,
    /** La lista llego rara y no se aplico nada: la tiene que mirar una persona. */
    RETENIDA,
    /** No se pudo bajar o leer la lista. */
    ERROR;

    /** Las corridas que dejan los precios al dia. */
    public boolean esBuena() {
        return this == ACTUALIZADA || this == SIN_CAMBIOS;
    }
}
