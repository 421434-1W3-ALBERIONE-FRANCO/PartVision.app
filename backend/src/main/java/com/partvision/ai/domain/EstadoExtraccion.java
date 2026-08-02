package com.partvision.ai.domain;

/**
 * Ciclo de vida de una extraccion IA.
 * PENDIENTE: la IA sugirio datos, falta revision humana.
 * CONFIRMADA: un humano reviso, corrigio y creo el producto oficial.
 * DESCARTADA: la extraccion no sirvio y se descarto.
 */
public enum EstadoExtraccion {
    PENDIENTE,
    CONFIRMADA,
    DESCARTADA
}
