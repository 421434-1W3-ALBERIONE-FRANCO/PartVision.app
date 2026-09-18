package com.partvision.compras.dto;

import java.util.List;

/**
 * Resultado de recibir la planilla: que paso con cada factura y que filas no se usaron. Se
 * devuelve 200 aunque haya conflictos o errores, porque el resto de las facturas se proceso
 * igual: el flujo puede mirar {@code conflictos} y {@code errores} para avisar por mail.
 */
public record RecepcionFilasResponse(
        int filasRecibidas,
        int filasIgnoradas,
        int facturas,
        int creadas,
        int actualizadas,
        int sinCambios,
        int conflictos,
        int errores,
        List<ResultadoFactura> resultados,
        List<FilaIgnorada> ignoradas
) {
    /** {@code resultado}: CREADA, ACTUALIZADA, SIN_CAMBIOS, CONFLICTO o ERROR. */
    public record ResultadoFactura(
            String factura,
            String resultado,
            String estado,
            Integer lineas,
            Integer lineasMatcheadas,
            String mensaje
    ) {
    }

    /** {@code posicion}: numero de fila dentro de lo enviado, contando desde 1. */
    public record FilaIgnorada(int posicion, String factura, String descripcion, String motivo) {
    }

    public static RecepcionFilasResponse de(int filasRecibidas, List<ResultadoFactura> resultados,
                                            List<FilaIgnorada> ignoradas) {
        return new RecepcionFilasResponse(
                filasRecibidas,
                ignoradas.size(),
                resultados.size(),
                contar(resultados, "CREADA"),
                contar(resultados, "ACTUALIZADA"),
                contar(resultados, "SIN_CAMBIOS"),
                contar(resultados, "CONFLICTO"),
                contar(resultados, "ERROR"),
                resultados,
                ignoradas);
    }

    private static int contar(List<ResultadoFactura> resultados, String tipo) {
        return (int) resultados.stream().filter(r -> tipo.equals(r.resultado())).count();
    }
}
