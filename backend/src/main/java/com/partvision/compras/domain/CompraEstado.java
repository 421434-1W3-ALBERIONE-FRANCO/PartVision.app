package com.partvision.compras.domain;

/**
 * El estado lo decide la planilla del cliente, salvo el ultimo paso:
 * <ul>
 *   <li>{@code EN_TRANSITO}: la planilla dice EN TRANSITO. La mercaderia no llego.</li>
 *   <li>{@code POR_UBICAR}: la planilla dice INGRESADA. Llego, pero todavia no esta en el
 *       stock: falta asignarle una ubicacion a cada linea en el panel.</li>
 *   <li>{@code INGRESADA}: se ubico en el panel y se cargo el stock. Es lo unico que decide
 *       PartVision, porque es lo unico que la planilla no sabe.</li>
 * </ul>
 */
public enum CompraEstado {
    EN_TRANSITO,
    POR_UBICAR,
    INGRESADA
}
