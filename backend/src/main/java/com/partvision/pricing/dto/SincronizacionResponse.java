package com.partvision.pricing.dto;

import com.partvision.pricing.domain.SincronizacionPrecio;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public record SincronizacionResponse(
        long id,
        String proveedor,
        String origen,
        boolean forzada,
        LocalDateTime iniciadaEn,
        LocalDateTime terminadaEn,
        String resultado,
        String mensaje,
        List<String> problemas,
        Long batchId,
        int filasLista,
        int actualizados,
        int sinCambio,
        int noEncontrados,
        int filasInvalidas,
        int enRevision,
        Integer conPrecioPropio
) {
    public static SincronizacionResponse from(SincronizacionPrecio s) {
        List<String> problemas = s.getProblemas() == null || s.getProblemas().isBlank()
                ? List.of()
                : Arrays.stream(s.getProblemas().split("\n")).filter(l -> !l.isBlank()).toList();
        return new SincronizacionResponse(s.getId(), s.getProveedor(), s.getOrigen().name(), s.isForzada(),
                s.getIniciadaEn(), s.getTerminadaEn(), s.getResultado().name(), s.getMensaje(), problemas,
                s.getBatchId(), s.getFilasLista(), s.getActualizados(), s.getSinCambio(), s.getNoEncontrados(),
                s.getFilasInvalidas(), s.getEnRevision(), s.getConPrecioPropio());
    }
}
