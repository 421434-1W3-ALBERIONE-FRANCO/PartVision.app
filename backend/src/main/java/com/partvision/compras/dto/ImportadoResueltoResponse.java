package com.partvision.compras.dto;

/**
 * Como quedo una linea importada. {@code stockCargado} dice si el stock entro en el momento
 * (compra ya ingresada) o si va a entrar al ingresar la compra.
 */
public record ImportadoResueltoResponse(
        Long lineaId,
        Long productoId,
        String sku,
        String descripcion,
        boolean stockCargado,
        String ubicacionCodigo,
        String mensaje
) {
}
