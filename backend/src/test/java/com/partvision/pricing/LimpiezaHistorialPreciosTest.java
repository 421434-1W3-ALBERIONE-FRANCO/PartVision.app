package com.partvision.pricing;

import com.partvision.imports.service.ProductoBulkImporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LimpiezaHistorialPreciosTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private Connection con;
    @Mock private Statement st;

    private PrecioImportService importService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws SQLException {
        importService = new PrecioImportService(null, null, null, null, mock(ProductoBulkImporter.class));
        when(jdbc.queryForObject(LimpiezaHistorialPrecios.TAMANIO, Long.class)).thenReturn(340L << 20, 40L << 20);
        when(con.createStatement()).thenReturn(st);
        when(jdbc.execute(any(ConnectionCallback.class)))
                .thenAnswer(inv -> inv.<ConnectionCallback<Object>>getArgument(0).doInConnection(con));
    }

    private LimpiezaHistorialPrecios limpieza(int intentos) {
        return new LimpiezaHistorialPrecios(jdbc, importService, intentos, 0);
    }

    @Test
    void borraDeATandasHastaQueNoQuedan_compactaYLiberaElCandado() throws Exception {
        when(jdbc.update(LimpiezaHistorialPrecios.BORRAR_TANDA, LimpiezaHistorialPrecios.TANDA))
                .thenReturn(20_000, 20_000, 7_215);

        LimpiezaHistorialPrecios.Resultado r = limpieza(1).limpiar();

        assertThat(r.corrio()).isTrue();
        assertThat(r.filasBorradas()).isEqualTo(47_215);
        assertThat(r.bytesAntes()).isEqualTo(340L << 20);
        assertThat(r.bytesDespues()).isEqualTo(40L << 20);
        verify(jdbc, times(3)).update(LimpiezaHistorialPrecios.BORRAR_TANDA, LimpiezaHistorialPrecios.TANDA);
        InOrder orden = inOrder(st);
        orden.verify(st).execute(LimpiezaHistorialPrecios.LIMITE_ESPERA);
        orden.verify(st).execute(LimpiezaHistorialPrecios.COMPACTAR);
        orden.verify(st).execute(LimpiezaHistorialPrecios.SIN_LIMITE);
        assertThat(importService.iniciarImport()).as("candado libre").isTrue();
    }

    @Test
    void sinNadaParaBorrar_igualCompacta() throws Exception {
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenReturn(0);

        assertThat(limpieza(1).limpiar().filasBorradas()).isZero();
        verify(st).execute(LimpiezaHistorialPrecios.COMPACTAR);
    }

    @Test
    void siNoPuedeCompactar_loBorradoQueda_yRestauraElLimiteDeEspera() throws Exception {
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenReturn(5);
        when(st.execute(LimpiezaHistorialPrecios.COMPACTAR)).thenThrow(new SQLException("lock timeout"));
        doAnswer(inv -> {
            try {
                return inv.<ConnectionCallback<Object>>getArgument(0).doInConnection(con);
            } catch (SQLException e) {
                throw new CannotAcquireLockException("canceling statement due to lock timeout", e);
            }
        }).when(jdbc).execute(any(ConnectionCallback.class));

        LimpiezaHistorialPrecios.Resultado r = limpieza(1).limpiar();

        assertThat(r.corrio()).isTrue();
        assertThat(r.filasBorradas()).isEqualTo(5);
        verify(st).execute(LimpiezaHistorialPrecios.SIN_LIMITE);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void siFallaElBorrado_igualLiberaElCandado() {
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenThrow(new IllegalStateException("base caida"));

        assertThatThrownBy(() -> limpieza(1).limpiar()).isInstanceOf(IllegalStateException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void conUnaImportacionEnCurso_reintentaYSiSigueOcupadaLaSaltea() {
        importService.iniciarImport();
        AtomicInteger esperas = new AtomicInteger();
        LimpiezaHistorialPrecios limpieza = new LimpiezaHistorialPrecios(jdbc, importService, 3, 0) {
            @Override
            boolean esperar() {
                esperas.incrementAndGet();
                return true;
            }
        };

        LimpiezaHistorialPrecios.Resultado r = limpieza.limpiar();

        assertThat(r.corrio()).isFalse();
        assertThat(esperas.get()).isEqualTo(2);
        verifyNoInteractions(jdbc);
        assertThat(importService.iniciarImport()).as("no le saca el candado a la importacion").isFalse();
    }

    @Test
    void siLaImportacionTerminaMientrasEspera_limpia() {
        importService.iniciarImport();
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenReturn(3);
        LimpiezaHistorialPrecios limpieza = new LimpiezaHistorialPrecios(jdbc, importService, 3, 0) {
            @Override
            boolean esperar() {
                importService.terminarImport();
                return true;
            }
        };

        assertThat(limpieza.limpiar().filasBorradas()).isEqualTo(3);
    }

    @Test
    void siLoInterrumpenMientrasEspera_noSigue() {
        importService.iniciarImport();
        Thread.currentThread().interrupt();

        // Espera de 1 minuto: con el hilo interrumpido, el sleep corta en el acto.
        LimpiezaHistorialPrecios.Resultado r = new LimpiezaHistorialPrecios(jdbc, importService, 5, 1).limpiar();

        assertThat(r.corrio()).isFalse();
        assertThat(Thread.interrupted()).isTrue();
        verifyNoInteractions(jdbc);
    }

    @Test
    void esperaElTiempoConfigurado() {
        assertThat(new LimpiezaHistorialPrecios(jdbc, importService, 1, 0).esperar()).isTrue();
    }

    @Test
    void programada_limpia() {
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenReturn(0);

        limpieza(1).programada();

        verify(jdbc).update(LimpiezaHistorialPrecios.BORRAR_TANDA, LimpiezaHistorialPrecios.TANDA);
    }

    @Test
    void tamanioDesconocido_cuentaComoCero() {
        when(jdbc.queryForObject(LimpiezaHistorialPrecios.TAMANIO, Long.class)).thenReturn(null);
        when(jdbc.update(anyString(), eq(LimpiezaHistorialPrecios.TANDA))).thenReturn(0);

        assertThat(limpieza(1).limpiar().bytesAntes()).isZero();
    }
}
