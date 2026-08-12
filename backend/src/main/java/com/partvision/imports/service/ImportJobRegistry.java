package com.partvision.imports.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria de los jobs de importación. Suficiente para una instancia
 * always-on (plan pago). Si el proceso reinicia, los jobs en curso se pierden
 * (la carga ya confirmada persiste igual, por el commit por fila).
 */
@Component
public class ImportJobRegistry {

    private final Map<String, ImportJob> jobs = new ConcurrentHashMap<>();

    public ImportJob crear(int total) {
        ImportJob job = new ImportJob(UUID.randomUUID().toString(), total);
        jobs.put(job.getId(), job);
        return job;
    }

    public ImportJob get(String id) {
        return jobs.get(id);
    }
}
