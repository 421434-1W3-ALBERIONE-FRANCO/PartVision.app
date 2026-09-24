package com.partvision.compras;

import com.partvision.common.exception.BusinessException;
import com.partvision.compras.ResultadoSincronizacion.Tipo;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.dto.FilaSheetRequest;
import com.partvision.compras.dto.RecepcionFilasResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La tabla "Ingreso stock" llega con una fila por producto. Los datos de ejemplo salen de la
 * planilla real del cliente, incluida la fila de anotacion "CONTROLO:" sin cantidad.
 */
@ExtendWith(MockitoExtension.class)
class RecepcionFilasServiceTest {

    private static final String TRANSITO = "EN TRÁNSITO";

    @Mock
    private CompraService compraService;

    private RecepcionFilasService service;

    @BeforeEach
    void setUp() {
        service = new RecepcionFilasService(compraService, new ProveedorResolver("ADS=Autopartes del Sur"));
        lenient().when(compraService.sincronizar(any())).thenAnswer(inv -> creada(inv.getArgument(0)));
    }

    private static FilaSheetRequest fila(String factura, String fecha, String codigo, String cantidad,
                                         String descripcion, String estatus) {
        return new FilaSheetRequest(factura, fecha, codigo, cantidad, descripcion, estatus, null);
    }

    private static FilaSheetRequest filaDe(String proveedor, String factura, String estatus) {
        return new FilaSheetRequest(factura, "24/08/2026", "272005", "2", "JUNTA TAPA", estatus, proveedor);
    }

    private static ResultadoSincronizacion creada(FacturaEntrante f) {
        Compra c = new Compra();
        c.setNumeroFactura(f.numero());
        c.setEstado(f.estado());
        return new ResultadoSincronizacion(Tipo.CREADA, c, f.lineas().size(), 0, "registrada");
    }

    private static ResultadoSincronizacion con(Tipo tipo, CompraEstado estado) {
        Compra c = new Compra();
        c.setEstado(estado);
        return new ResultadoSincronizacion(tipo, c, 1, 1, tipo.name().toLowerCase());
    }

    /** Filas 2 a 8 de la captura de la planilla, tal cual. */
    @Test
    void laPlanillaReal_seAgrupaPorFacturaYSaltaLaAnotacion() {
        List<FilaSheetRequest> filas = List.of(
                fila("900004109", "24/08/2026", "272005", "2", "JUNTA TAPA CILINDRO FIAT", TRANSITO),
                fila("900004110", "24/08/2026", "808449(05)", "1", "AROS RECTIFICACION VOLKSWAGEN", TRANSITO),
                fila("900004110", "24/08/2026", "SCVJEA*19*2", "1", "SUBCONJUNTO Vw JEA/BER", TRANSITO),
                fila("900004110", "24/08/2026", "400300-R", "1", "JUEGO DE JUNTAS PERKINS", TRANSITO),
                fila("900004111", "24/08/2026", "351211", "2", "JUNTA DE CARTER MERCEDES BENZ", TRANSITO),
                fila("900000482", "25/08/2026", "", "2", "bie0381 biela om651", TRANSITO),
                fila("900000482", "25/08/2026", "", "", "CONTROLO:", TRANSITO));

        RecepcionFilasResponse resp = service.recibir(filas);

        assertThat(resp.filasRecibidas()).isEqualTo(7);
        assertThat(resp.facturas()).isEqualTo(4);
        assertThat(resp.creadas()).isEqualTo(4);
        assertThat(resp.resultados()).extracting(RecepcionFilasResponse.ResultadoFactura::factura)
                .containsExactly("900004109", "900004110", "900004111", "900000482");

        assertThat(resp.filasIgnoradas()).isEqualTo(1);
        RecepcionFilasResponse.FilaIgnorada ignorada = resp.ignoradas().get(0);
        assertThat(ignorada.posicion()).isEqualTo(7);
        assertThat(ignorada.factura()).isEqualTo("900000482");
        assertThat(ignorada.descripcion()).isEqualTo("CONTROLO:");

        ArgumentCaptor<FacturaEntrante> captor = ArgumentCaptor.forClass(FacturaEntrante.class);
        verify(compraService, times(4)).sincronizar(captor.capture());
        FacturaEntrante tresLineas = captor.getAllValues().get(1);
        assertThat(tresLineas.lineas()).hasSize(3);
        assertThat(tresLineas.fecha()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(tresLineas.estado()).isEqualTo(CompraEstado.EN_TRANSITO);

        FacturaEntrante sinCodigo = captor.getAllValues().get(3);
        assertThat(sinCodigo.lineas()).hasSize(1);
        assertThat(sinCodigo.lineas().get(0).codigo()).isEqualTo("IMPORTADOS");
        assertThat(sinCodigo.lineas().get(0).cantidad()).isEqualTo(2);
    }

    @Test
    void filaSinNumeroDeFactura_seIgnora() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("  ", "24/08/2026", "272005", "2", "sin factura", TRANSITO)));

        assertThat(resp.facturas()).isZero();
        assertThat(resp.ignoradas().get(0).motivo()).contains("sin numero de factura");
        verify(compraService, never()).sincronizar(any());
    }

    /**
     * El 2026-09-24 llego una fila con cantidad 1.197.421 y la descripcion partida en dos
     * lineas: en la planilla se le habia colado el valor de otra columna. Entraria como una
     * compra normal y, al resolverla e ingresarla, cargaria ese disparate al stock.
     */
    @Test
    void filaConCantidadInverosimil_seIgnoraYSeExplica() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004152", "24/08/2026", "", "1197421", "1.00\n04178311 std jgo aros", TRANSITO)));

        assertThat(resp.facturas()).isZero();
        assertThat(resp.ignoradas()).hasSize(1);
        assertThat(resp.ignoradas().get(0).motivo())
                .contains("cantidad inverosimil")
                .contains("1197421");
        assertThat(resp.ignoradas().get(0).factura()).isEqualTo("900004152");
        verify(compraService, never()).sincronizar(any());
    }

    /** El tope no puede dejar afuera una compra real: 240 retenes es una fila legitima. */
    @Test
    void cantidadGrandePeroPosible_entra() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004151", "24/08/2026", "PKRV-043", "240", "RET GUIA DE VALVULA", TRANSITO),
                fila("900004151", "24/08/2026", "TBI610", "10000", "TAPON TAZA", TRANSITO)));

        assertThat(resp.ignoradas()).isEmpty();
        assertThat(resp.facturas()).isEqualTo(1);
    }

    @Test
    void todasLasFilasIngresadas_llegaPorUbicar() {
        service.recibir(List.of(
                fila("900004113", "25/08/2026", "JGPEP6T*AK", "1", "Bulones", "INGRESADA"),
                fila("900004113", "25/08/2026", "JTPTU5J*E", "2", "Junta", "INGRESADA")));

        verify(compraService).sincronizar(argThat(f -> f.estado() == CompraEstado.POR_UBICAR));
    }

    /** Si algunas filas dicen INGRESADA y otras no, no se adelanta: sigue en transito. */
    @Test
    void estadoMixto_quedaEnTransitoYLoAvisa() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004113", "25/08/2026", "JGPEP6T*AK", "1", "Bulones", "INGRESADA"),
                fila("900004113", "25/08/2026", "JTPTU5J*E", "2", "Junta", TRANSITO)));

        verify(compraService).sincronizar(argThat(f -> f.estado() == CompraEstado.EN_TRANSITO));
        assertThat(resp.resultados().get(0).mensaje()).contains("filas INGRESADA y filas EN TRANSITO");
    }

    @Test
    void proveedorConAlias_llegaConElNombreDelCatalogo() {
        service.recibir(List.of(filaDe("ADS", "900004114", TRANSITO)));

        verify(compraService).sincronizar(argThat(f -> "Autopartes del Sur".equals(f.proveedor())));
    }

    @Test
    void sinProveedor_llegaSinProveedor() {
        service.recibir(List.of(filaDe(null, "900004114", TRANSITO)));

        verify(compraService).sincronizar(argThat(f -> f.proveedor() == null));
    }

    // --- facturas que no se pueden armar: vuelven con error y el resto sigue ---

    @Test
    void fechasDistintasEnUnaFactura_esErrorYNoFrenaALasDemas() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004115", "26/08/2026", "260907", "1", "Junta", TRANSITO),
                fila("900004115", "27/08/2026", "351210", "1", "Junta", TRANSITO),
                fila("900004116", "26/08/2026", "922310", "2", "Junta", TRANSITO)));

        assertThat(resp.errores()).isEqualTo(1);
        assertThat(resp.creadas()).isEqualTo(1);
        assertThat(resp.resultados().get(0).mensaje()).contains("fechas distintas");
        verify(compraService, times(1)).sincronizar(any());
    }

    @Test
    void facturaSinFecha_esError() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004115", " ", "260907", "1", "Junta", TRANSITO)));

        assertThat(resp.resultados().get(0).resultado()).isEqualTo("ERROR");
        assertThat(resp.resultados().get(0).mensaje()).contains("Falta la fecha");
    }

    @Test
    void facturaConFechaIlegible_esError() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                fila("900004115", "mañana", "260907", "1", "Junta", TRANSITO)));

        assertThat(resp.resultados().get(0).mensaje()).contains("Formato de fecha inválido");
    }

    @Test
    void proveedoresDistintosEnUnaFactura_esError() {
        RecepcionFilasResponse resp = service.recibir(List.of(
                filaDe("EGSA", "900004114", TRANSITO),
                filaDe("ADS", "900004114", TRANSITO)));

        assertThat(resp.resultados().get(0).resultado()).isEqualTo("ERROR");
        assertThat(resp.resultados().get(0).mensaje()).contains("proveedores distintos");
        verify(compraService, never()).sincronizar(any());
    }

    @Test
    void errorDeNegocioAlGuardar_vuelveConSuMensaje() {
        doThrow(new BusinessException("Compra no valida")).when(compraService).sincronizar(any());

        RecepcionFilasResponse resp = service.recibir(List.of(filaDe(null, "900004114", TRANSITO)));

        assertThat(resp.resultados().get(0).resultado()).isEqualTo("ERROR");
        assertThat(resp.resultados().get(0).mensaje()).isEqualTo("Compra no valida");
    }

    /** Una falla inesperada en una factura no puede tirar el resto de la planilla. */
    @Test
    void falloInesperado_seInformaSinDetallesYSigueConLaSiguiente() {
        doThrow(new IllegalStateException("pool de conexiones agotado"))
                .doAnswer(inv -> creada(inv.getArgument(0)))
                .when(compraService).sincronizar(any());

        RecepcionFilasResponse resp = service.recibir(List.of(
                filaDe(null, "900004114", TRANSITO),
                filaDe(null, "900004115", TRANSITO)));

        assertThat(resp.errores()).isEqualTo(1);
        assertThat(resp.creadas()).isEqualTo(1);
        assertThat(resp.resultados().get(0).mensaje())
                .isEqualTo("Error interno al procesar la factura")
                .doesNotContain("pool");
    }

    @Test
    void losContadoresReflejanCadaResultado() {
        doReturn(con(Tipo.ACTUALIZADA, CompraEstado.POR_UBICAR))
                .doReturn(con(Tipo.SIN_CAMBIOS, CompraEstado.EN_TRANSITO))
                .doReturn(con(Tipo.CONFLICTO, CompraEstado.INGRESADA))
                .when(compraService).sincronizar(any());

        RecepcionFilasResponse resp = service.recibir(List.of(
                filaDe(null, "1", TRANSITO), filaDe(null, "2", TRANSITO), filaDe(null, "3", TRANSITO)));

        assertThat(resp.actualizadas()).isEqualTo(1);
        assertThat(resp.sinCambios()).isEqualTo(1);
        assertThat(resp.conflictos()).isEqualTo(1);
        assertThat(resp.resultados()).extracting(RecepcionFilasResponse.ResultadoFactura::estado)
                .containsExactly("POR_UBICAR", "EN_TRANSITO", "INGRESADA");
    }
}
