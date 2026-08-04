package com.partvision.location.domain;

/**
 * Tipos de ubicacion fisica del deposito. Cada tipo tiene una profundidad que
 * define la jerarquia permitida: un hijo debe ser mas profundo que su padre.
 * La profundidad se define explicita (no por ordinal) para no depender del
 * orden de declaracion; ESTANTE y PALLET comparten nivel (ambos cuelgan de un
 * pasillo), y OTRO es un tipo flexible que puede colgar de cualquiera.
 */
public enum TipoUbicacion {
    DEPOSITO(0),
    PASILLO(1),
    ESTANTE(2),
    PALLET(2),
    CAJON(3),
    OTRO(4);

    private final int profundidad;

    TipoUbicacion(int profundidad) {
        this.profundidad = profundidad;
    }

    public int getProfundidad() {
        return profundidad;
    }
}
