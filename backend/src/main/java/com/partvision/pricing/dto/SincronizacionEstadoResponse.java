package com.partvision.pricing.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Todo lo que muestra el panel de actualizacion automatica de una lista en la pantalla Precios. */
public record SincronizacionEstadoResponse(
        boolean habilitada,
        String proveedor,
        /* true si la lista nos la manda el robot del cliente (EGSA); false si la bajamos nosotros (ADS) */
        boolean recibeArchivo,
        boolean enCurso,
        SincronizacionResponse ultima,
        /* Ultima vez que los precios quedaron al dia (actualizada, sin cambios, o cargada a mano). */
        LocalDateTime ultimaBuenaEn,
        long pendientesRevision,
        AlertaPreciosResponse alerta,
        List<SincronizacionResponse> historial,
        /* Hay una lista retenida guardada para tocar "Aplicar igual". */
        boolean hayListaRetenida
) {}
