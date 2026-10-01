package com.partvision.pricing.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Todo lo que muestra el panel de actualizacion automatica en la pantalla Precios. */
public record SincronizacionEstadoResponse(
        boolean habilitada,
        String proveedor,
        boolean enCurso,
        SincronizacionResponse ultima,
        /* Ultima vez que los precios quedaron al dia (actualizada o sin cambios). */
        LocalDateTime ultimaBuenaEn,
        long pendientesRevision,
        AlertaPreciosResponse alerta,
        List<SincronizacionResponse> historial
) {}
