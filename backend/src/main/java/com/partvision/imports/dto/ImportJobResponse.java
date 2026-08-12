package com.partvision.imports.dto;

import com.partvision.imports.service.ImportJob;

import java.util.List;

/**
 * Estado/progreso de una importación asíncrona (para el polling del front).
 */
public record ImportJobResponse(
        String jobId,
        String estado,
        int total,
        int procesados,
        int importados,
        int omitidos,
        int porcentaje,
        List<ImportResultResponse.FilaError> errores,
        String errorGeneral
) {
    public static ImportJobResponse from(ImportJob job) {
        int porcentaje = job.getTotal() <= 0
                ? 100
                : (int) Math.min(100, Math.round(job.getProcesados() * 100.0 / job.getTotal()));
        return new ImportJobResponse(
                job.getId(),
                job.getEstado().name(),
                job.getTotal(),
                job.getProcesados(),
                job.getImportados(),
                job.getOmitidos(),
                porcentaje,
                job.getErrores(),
                job.getErrorGeneral());
    }
}
