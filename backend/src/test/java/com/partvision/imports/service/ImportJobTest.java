package com.partvision.imports.service;

import com.partvision.imports.dto.ImportResultResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportJobTest {

    @Test
    void nuevoJob_estadoEnCurso() {
        ImportJob job = new ImportJob("abc", 10);

        assertThat(job.getId()).isEqualTo("abc");
        assertThat(job.getTotal()).isEqualTo(10);
        assertThat(job.getEstado()).isEqualTo(ImportJob.Estado.EN_CURSO);
        assertThat(job.getProcesados()).isZero();
        assertThat(job.getImportados()).isZero();
        assertThat(job.getOmitidos()).isZero();
        assertThat(job.getErrores()).isEmpty();
        assertThat(job.getErrorGeneral()).isNull();
    }

    @Test
    void marcarProcesada_incrementaContador() {
        ImportJob job = new ImportJob("x", 5);

        job.marcarProcesada();
        job.marcarProcesada();

        assertThat(job.getProcesados()).isEqualTo(2);
    }

    @Test
    void marcarImportada_incrementaContador() {
        ImportJob job = new ImportJob("x", 5);

        job.marcarImportada();

        assertThat(job.getImportados()).isEqualTo(1);
    }

    @Test
    void marcarOmitida_incrementaContador() {
        ImportJob job = new ImportJob("x", 5);

        job.marcarOmitida();
        job.marcarOmitida();
        job.marcarOmitida();

        assertThat(job.getOmitidos()).isEqualTo(3);
    }

    @Test
    void agregarError_seAgregaALista() {
        ImportJob job = new ImportJob("x", 5);

        job.agregarError(new ImportResultResponse.FilaError(1, "fallo"));
        job.agregarError(new ImportResultResponse.FilaError(3, "otro fallo"));

        assertThat(job.getErrores()).hasSize(2);
        assertThat(job.getErrores().get(0).fila()).isEqualTo(1);
        assertThat(job.getErrores().get(1).mensaje()).isEqualTo("otro fallo");
    }

    @Test
    void completar_cambiaEstado() {
        ImportJob job = new ImportJob("x", 5);

        job.completar();

        assertThat(job.getEstado()).isEqualTo(ImportJob.Estado.COMPLETADO);
    }

    @Test
    void fallar_cambiaEstadoYGuardaMensaje() {
        ImportJob job = new ImportJob("x", 5);

        job.fallar("exploto todo");

        assertThat(job.getEstado()).isEqualTo(ImportJob.Estado.ERROR);
        assertThat(job.getErrorGeneral()).isEqualTo("exploto todo");
    }
}
