package com.partvision.pricing.domain;

public enum EstadoRevisionPrecio {
    PENDIENTE,
    APLICADA,
    DESCARTADA,
    /** Una corrida mas nueva trajo otro precio para el mismo producto. */
    VENCIDA
}
