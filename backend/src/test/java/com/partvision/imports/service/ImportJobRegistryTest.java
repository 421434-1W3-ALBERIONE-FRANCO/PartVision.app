package com.partvision.imports.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportJobRegistryTest {

    @Test
    void crear_devuelveJobConId() {
        ImportJobRegistry registry = new ImportJobRegistry();

        ImportJob job = registry.crear(100);

        assertThat(job.getId()).isNotBlank();
        assertThat(job.getTotal()).isEqualTo(100);
        assertThat(job.getEstado()).isEqualTo(ImportJob.Estado.EN_CURSO);
    }

    @Test
    void get_jobExistente() {
        ImportJobRegistry registry = new ImportJobRegistry();
        ImportJob job = registry.crear(50);

        ImportJob encontrado = registry.get(job.getId());

        assertThat(encontrado).isSameAs(job);
    }

    @Test
    void get_jobInexistente_devuelveNull() {
        ImportJobRegistry registry = new ImportJobRegistry();

        assertThat(registry.get("no-existe")).isNull();
    }
}
