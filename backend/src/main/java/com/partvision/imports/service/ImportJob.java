package com.partvision.imports.service;

import com.partvision.imports.dto.ImportResultResponse.FilaError;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado mutable de una importación asíncrona. Lo actualiza el hilo de fondo
 * mientras procesa, y lo lee el endpoint de estado (polling). Los contadores
 * son atómicos y el estado {@code volatile} para que la lectura concurrente sea segura.
 */
public class ImportJob {

    public enum Estado { EN_CURSO, COMPLETADO, ERROR }

    private final String id;
    private final int total;
    private final AtomicInteger procesados = new AtomicInteger();
    private final AtomicInteger importados = new AtomicInteger();
    private final AtomicInteger omitidos = new AtomicInteger();
    private final List<FilaError> errores = new CopyOnWriteArrayList<>();
    private volatile Estado estado = Estado.EN_CURSO;
    private volatile String errorGeneral;

    public ImportJob(String id, int total) {
        this.id = id;
        this.total = total;
    }

    public void marcarProcesada() {
        procesados.incrementAndGet();
    }

    public void marcarImportada() {
        importados.incrementAndGet();
    }

    public void marcarOmitida() {
        omitidos.incrementAndGet();
    }

    public void agregarError(FilaError error) {
        errores.add(error);
    }

    public void completar() {
        this.estado = Estado.COMPLETADO;
    }

    public void fallar(String mensaje) {
        this.estado = Estado.ERROR;
        this.errorGeneral = mensaje;
    }

    public String getId() {
        return id;
    }

    public int getTotal() {
        return total;
    }

    public int getProcesados() {
        return procesados.get();
    }

    public int getImportados() {
        return importados.get();
    }

    public int getOmitidos() {
        return omitidos.get();
    }

    public List<FilaError> getErrores() {
        return errores;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getErrorGeneral() {
        return errorGeneral;
    }
}
