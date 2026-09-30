package com.partvision.compras.domain;

/**
 * Que se decidio sobre una linea con una cantidad fuera de lo normal. Una linea con cantidad
 * normal no tiene revision (null). Ver V30.
 */
public enum RevisionLinea {
    /** Supera el tope y nadie la miro todavia: la compra no se puede ingresar. */
    PENDIENTE,
    /** La cantidad es real: entra al stock como cualquier otra. */
    ACEPTADA,
    /** Es un error de la planilla: queda registrada, pero no entra al stock ni a los totales. */
    DESCARTADA
}
