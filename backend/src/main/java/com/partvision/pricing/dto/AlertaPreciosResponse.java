package com.partvision.pricing.dto;

/**
 * El aviso que ve un administrador en cualquier pantalla cuando la actualizacion de precios
 * necesita que alguien la mire.
 *
 * @param nivel ERROR (los precios pueden estar desactualizados) o AVISO (hay algo para revisar)
 */
public record AlertaPreciosResponse(String nivel, String mensaje) {

    public static AlertaPreciosResponse error(String mensaje) {
        return new AlertaPreciosResponse("ERROR", mensaje);
    }

    public static AlertaPreciosResponse aviso(String mensaje) {
        return new AlertaPreciosResponse("AVISO", mensaje);
    }
}
