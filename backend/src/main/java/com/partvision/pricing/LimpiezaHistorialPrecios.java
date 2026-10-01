package com.partvision.pricing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.time.Duration;

/**
 * Limpieza semanal del historial de precios. Cada importacion manual escribe una fila por
 * producto aunque el precio no haya cambiado: al 2026-10-01 eran 2,1 de los 2,4 millones de
 * filas (88%, unos 300 MB de una base de 553 MB en un disco compartido con NexVia).
 *
 * <p>Se borran SOLO esas filas: no sirven para revertir (volver "al precio anterior" las deja
 * igual) ni dicen nada del negocio. Los cambios reales se conservan siempre, porque son los
 * que permiten revertir un batch y los que va a analizar el modulo de Informes.
 *
 * <p>Borra de a tandas para no inflar el WAL de Postgres de una sola vez, y despues compacta
 * la tabla para devolverle el espacio al disco. Toma el candado de las importaciones: nunca
 * corre a la vez que una.
 */
@Slf4j
@Component
public class LimpiezaHistorialPrecios {

    static final String BORRAR_TANDA = """
            delete from historial_precios where id in (
                select id from historial_precios
                 where precio_costo_anterior is not distinct from precio_costo_nuevo
                   and precio_venta_anterior is not distinct from precio_venta_nuevo
                 limit ?)""";
    static final String TAMANIO = "select pg_total_relation_size('historial_precios')";
    /** Si algo tiene tomada la tabla (por ejemplo, revirtiendo un batch), no esperar para siempre. */
    static final String LIMITE_ESPERA = "set lock_timeout = '60s'";
    static final String COMPACTAR = "vacuum (full, analyze) historial_precios";
    static final String SIN_LIMITE = "reset lock_timeout";

    static final int TANDA = 20_000;

    private final JdbcTemplate jdbc;
    private final PrecioImportService importService;
    private final int intentos;
    private final Duration espera;

    public LimpiezaHistorialPrecios(JdbcTemplate jdbc, PrecioImportService importService,
                                    @Value("${partvision.precios.limpieza.intentos:6}") int intentos,
                                    @Value("${partvision.precios.limpieza.espera-minutos:10}") long esperaMinutos) {
        this.jdbc = jdbc;
        this.importService = importService;
        this.intentos = intentos;
        this.espera = Duration.ofMinutes(esperaMinutos);
    }

    /** Resultado de una limpieza, para el log y los tests. */
    record Resultado(boolean corrio, long filasBorradas, long bytesAntes, long bytesDespues) {}

    @Scheduled(cron = "${partvision.precios.limpieza.cron:0 0 4 * * SUN}", zone = "America/Argentina/Buenos_Aires")
    public void programada() {
        limpiar();
    }

    /** Si hay una importacion en curso, espera y reintenta; si sigue ocupada, queda para la semana que viene. */
    Resultado limpiar() {
        for (int intento = 1; intento <= intentos; intento++) {
            if (importService.iniciarImport()) {
                try {
                    return limpiarConCandado();
                } finally {
                    importService.terminarImport();
                }
            }
            log.info("Limpieza del historial: hay una importacion en curso (intento {} de {})", intento, intentos);
            if (intento < intentos && !esperar()) break;
        }
        log.warn("Limpieza del historial salteada: siempre hubo una importacion en curso. Se reintenta la semana que viene");
        return new Resultado(false, 0, 0, 0);
    }

    private Resultado limpiarConCandado() {
        long antes = tamanio();
        long borradas = 0;
        int tanda;
        do {
            tanda = jdbc.update(BORRAR_TANDA, TANDA);
            borradas += tanda;
        } while (tanda == TANDA);

        try {
            compactar();
        } catch (RuntimeException e) {
            // Lo borrado ya esta borrado; sin compactar, el espacio igual se reusa en las proximas importaciones.
            log.warn("Limpieza del historial: no se pudo compactar la tabla ({})", e.getMessage());
        }
        long despues = tamanio();
        log.info("Limpieza del historial de precios: {} filas sin cambio borradas, {} MB -> {} MB",
                borradas, antes / (1024 * 1024), despues / (1024 * 1024));
        return new Resultado(true, borradas, antes, despues);
    }

    /**
     * Todo en la MISMA conexion: el pool puede dar una distinta en cada llamada, y el limite
     * de espera tiene que valer para el vacuum. Vacuum no puede ir dentro de una transaccion;
     * aca no hay ninguna (autocommit).
     */
    private void compactar() {
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute(LIMITE_ESPERA);
                try {
                    st.execute(COMPACTAR);
                } finally {
                    st.execute(SIN_LIMITE);
                }
            }
            return null;
        });
    }

    private long tamanio() {
        Long bytes = jdbc.queryForObject(TAMANIO, Long.class);
        return bytes == null ? 0 : bytes;
    }

    /** @return false si interrumpieron el hilo (se apaga el servidor): no tiene sentido seguir */
    boolean esperar() {
        try {
            Thread.sleep(espera.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
