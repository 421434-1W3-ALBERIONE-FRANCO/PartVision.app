package com.partvision.location.domain;

/**
 * Tipos de ubicacion, de menor a mayor profundidad en la jerarquia.
 * La profundidad se define explicita (no por ordinal) para no depender del
 * orden de declaracion.
 */
public enum TipoUbicacion {
    DEPOSITO(0),
    SECTOR(1),
    PASILLO(2),
    ESTANTERIA(3),
    NIVEL(4);

    private final int profundidad;

    TipoUbicacion(int profundidad) {
        this.profundidad = profundidad;
    }

    public int getProfundidad() {
        return profundidad;
    }
}
