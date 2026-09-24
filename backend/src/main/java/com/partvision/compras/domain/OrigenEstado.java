package com.partvision.compras.domain;

/**
 * Quien dejo la compra en el estado que tiene. Hace falta para no pelear con la planilla: si
 * alguien ingresa una compra desde el panel antes de que la planilla la marque INGRESADA, la
 * diferencia es a proposito y no tiene que aparecer como conflicto en cada envio del flujo.
 * En cambio, si la planilla misma dijo INGRESADA y despues vuelve a EN TRANSITO con el stock
 * ya cargado, eso si es alguien editando la planilla hacia atras y hay que avisar.
 */
public enum OrigenEstado {
    /** Lo dijo la planilla del cliente, a traves de Power Automate. */
    PLANILLA,
    /** Lo hizo una persona en el panel de PartVision. */
    PANEL
}
