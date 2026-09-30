package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.ResultadoSincronizacion.Tipo;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.domain.RevisionLinea;
import com.partvision.compras.dto.CambiarEstadoRequest;
import com.partvision.compras.dto.CompraResponse;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.inventory.service.StockService;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lineas con una cantidad fuera de lo normal (mas de {@value CompraLinea#CANTIDAD_PARA_REVISAR}).
 * Entran, pero una persona decide si se aceptan o se descartan antes de que carguen stock. El
 * 2026-09-24 la planilla trajo una fila con 1.197.421 unidades; antes se rechazaba sola, y el
 * usuario pidio que la decision sea de alguien que conoce el negocio.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceRevisionTest {

    @Mock private CompraRepository compraRepo;
    @Mock private ProductoRepository productoRepo;
    @Mock private StockService stockService;
    @Mock private StockRepository stockRepository;
    @Mock private UbicacionService ubicacionService;

    private CompraService service;

    @BeforeEach
    void setUp() {
        service = new CompraService(compraRepo, productoRepo, stockService, stockRepository, ubicacionService,
                new ProveedorResolver("ADS=Autopartes del Sur"));
        lenient().when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> {
            Compra c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
                c.setCreatedAt(Instant.now());
            }
            return c;
        });
    }

    private static FacturaEntrante factura(FacturaEntrante.Linea... lineas) {
        return new FacturaEntrante("900004152", LocalDate.of(2026, 9, 16), "Autopartes del Sur",
                CompraEstado.EN_TRANSITO, List.of(lineas));
    }

    private static FacturaEntrante.Linea linea(String codigo, int cantidad) {
        return new FacturaEntrante.Linea(codigo, "04178311 std jgo aros", cantidad);
    }

    /** Una compra guardada con dos lineas: una normal (id 1) y una fuera de lo normal (id 2). */
    private static Compra conUnaLineaParaRevisar(CompraEstado estado, RevisionLinea revision) {
        Compra c = new Compra();
        c.setId(51L);
        c.setNumeroFactura("900004152");
        c.setFechaFactura(LocalDate.of(2026, 9, 16));
        c.setProveedor("Autopartes del Sur");
        c.setEstado(estado);
        c.setCreatedAt(Instant.now());

        CompraLinea normal = new CompraLinea();
        normal.setId(1L);
        normal.setCodigo("272005");
        normal.setDescripcion("junta");
        normal.setCantidad(2);
        normal.setProducto(producto(10L));
        c.addLinea(normal);

        CompraLinea rara = new CompraLinea();
        rara.setId(2L);
        rara.setCodigo("IMPORTADOS");
        rara.setDescripcion("04178311 std jgo aros");
        rara.setCantidad(1197421);
        rara.setProducto(producto(11L));
        rara.setRevision(revision);
        c.addLinea(rara);
        return c;
    }

    private static Producto producto(Long id) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku("SKU-" + id);
        p.setDescripcion("producto " + id);
        p.setEstado(ProductoEstado.ACTIVO);
        return p;
    }

    private static CambiarEstadoRequest ambasEn(Long ubicacionId) {
        return new CambiarEstadoRequest(List.of(
                new CambiarEstadoRequest.LineaUbicacion(1L, ubicacionId),
                new CambiarEstadoRequest.LineaUbicacion(2L, ubicacionId)));
    }

    private static Ubicacion ubicacion(Long id) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setCodigo("A-01");
        return u;
    }

    // --- al recibirla ---

    @Test
    void alRecibirla_laQueSuperaElTope_quedaPendiente() {
        when(compraRepo.findByNumeroFactura("900004152")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        ResultadoSincronizacion r = service.sincronizar(factura(linea("272005", 2), linea("IMPORTADOS", 1197421)));

        assertThat(r.tipo()).isEqualTo(Tipo.CREADA);
        assertThat(r.compra().getLineas().get(0).getRevision()).isNull();
        assertThat(r.compra().getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.PENDIENTE);
        assertThat(r.mensaje()).contains("1 linea(s)").contains("revisar");
    }

    /** El tope es "mas de 300": justo 300 es normal. */
    @Test
    void alRecibirla_justoEnElTope_noSeMarca() {
        when(compraRepo.findByNumeroFactura("900004152")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        ResultadoSincronizacion r = service.sincronizar(factura(
                linea("272005", CompraLinea.CANTIDAD_PARA_REVISAR),
                linea("272006", CompraLinea.CANTIDAD_PARA_REVISAR + 1)));

        assertThat(r.compra().getLineas().get(0).getRevision()).isNull();
        assertThat(r.compra().getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.PENDIENTE);
    }

    @Test
    void alRecibirla_todasNormales_elMensajeNoDiceNada() {
        when(compraRepo.findByNumeroFactura("900004152")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        ResultadoSincronizacion r = service.sincronizar(factura(linea("272005", 240)));

        assertThat(r.mensaje()).isEqualTo("registrada");
    }

    /**
     * La decision no se pierde con el envio siguiente: la revision no es parte del contenido
     * de la factura, asi que la planilla repitiendo la misma fila no da conflicto ni la resetea.
     */
    @Test
    void reenvioDeLaPlanilla_despuesDeDescartar_noEsConflictoNiLaResetea() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.DESCARTADA);
        compra.getLineas().get(0).setDescripcion("04178311 std jgo aros");
        when(compraRepo.findByNumeroFactura("900004152")).thenReturn(Optional.of(compra));

        ResultadoSincronizacion r = service.sincronizar(factura(linea("272005", 2), linea("IMPORTADOS", 1197421)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        assertThat(compra.getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.DESCARTADA);
    }

    // --- ingresar ---

    @Test
    void ingresar_conUnaLineaSinRevisar_seRechaza() {
        when(compraRepo.findWithLineasById(51L))
                .thenReturn(Optional.of(conUnaLineaParaRevisar(CompraEstado.POR_UBICAR, RevisionLinea.PENDIENTE)));

        assertThatThrownBy(() -> service.marcarIngresada(51L, ambasEn(50L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 linea(s)")
                .hasMessageContaining("aceptarlas o descartarlas");
        verify(stockService, never()).registrarEntrada(any());
    }

    /** Descartada: la compra se ingresa, pero esa linea no carga stock. */
    @Test
    void ingresar_conUnaLineaDescartada_noCargaSuStock() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.POR_UBICAR, RevisionLinea.DESCARTADA);
        when(compraRepo.findWithLineasById(51L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ubicacion(50L));

        service.marcarIngresada(51L, ambasEn(50L));

        ArgumentCaptor<EntradaRequest> entrada = ArgumentCaptor.forClass(EntradaRequest.class);
        verify(stockService).registrarEntrada(entrada.capture());   // una sola: la normal
        assertThat(entrada.getValue().productoId()).isEqualTo(10L);
        assertThat(compra.getLineas().get(1).getUbicacionIngreso()).isNull();
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    /** Aceptada: entra al stock como cualquier otra, con su cantidad. */
    @Test
    void ingresar_conUnaLineaAceptada_cargaSuStock() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.POR_UBICAR, RevisionLinea.ACEPTADA);
        when(compraRepo.findWithLineasById(51L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ubicacion(50L));

        service.marcarIngresada(51L, ambasEn(50L));

        ArgumentCaptor<EntradaRequest> entradas = ArgumentCaptor.forClass(EntradaRequest.class);
        verify(stockService, org.mockito.Mockito.times(2)).registrarEntrada(entradas.capture());
        assertThat(entradas.getAllValues()).extracting(EntradaRequest::cantidad).containsExactly(2, 1197421);
    }

    // --- aceptar o descartar ---

    @Test
    void revisar_descartar_marcaLaLineaSinBorrarla() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.PENDIENTE);
        when(compraRepo.findWithLineasById(51L)).thenReturn(Optional.of(compra));

        CompraResponse r = service.revisarLinea(51L, 2L, RevisionLinea.DESCARTADA);

        assertThat(compra.getLineas()).hasSize(2);
        assertThat(compra.getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.DESCARTADA);
        assertThat(r.totalUnidades()).isEqualTo(2);             // la descartada no suma
        assertThat(r.lineasEnRevision()).isEmpty();
        assertThat(r.lineas().get(1).revision()).isEqualTo("DESCARTADA");
    }

    @Test
    void revisar_aceptar_laDejaEntrarConSuCantidad() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.PENDIENTE);
        when(compraRepo.findWithLineasById(51L)).thenReturn(Optional.of(compra));

        CompraResponse r = service.revisarLinea(51L, 2L, RevisionLinea.ACEPTADA);

        assertThat(compra.getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.ACEPTADA);
        assertThat(r.totalUnidades()).isEqualTo(2 + 1197421);
    }

    /** Mientras no se ingreso, se puede cambiar de opinion. */
    @Test
    void revisar_cambiarDeOpinion_antesDeIngresar() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.POR_UBICAR, RevisionLinea.DESCARTADA);
        when(compraRepo.findWithLineasById(51L)).thenReturn(Optional.of(compra));

        service.revisarLinea(51L, 2L, RevisionLinea.ACEPTADA);

        assertThat(compra.getLineas().get(1).getRevision()).isEqualTo(RevisionLinea.ACEPTADA);
    }

    /** Ya ingresada, el stock se cargo con esa decision: cambiarla lo dejaria desfasado. */
    @Test
    void revisar_compraYaIngresada_seRechaza() {
        when(compraRepo.findWithLineasById(51L))
                .thenReturn(Optional.of(conUnaLineaParaRevisar(CompraEstado.INGRESADA, RevisionLinea.ACEPTADA)));

        assertThatThrownBy(() -> service.revisarLinea(51L, 2L, RevisionLinea.DESCARTADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("revertir el ingreso");
    }

    @Test
    void revisar_unaLineaNormal_seRechaza() {
        when(compraRepo.findWithLineasById(51L))
                .thenReturn(Optional.of(conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.PENDIENTE)));

        assertThatThrownBy(() -> service.revisarLinea(51L, 1L, RevisionLinea.DESCARTADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cantidad normal");
    }

    @Test
    void revisar_unaLineaDeOtraCompra_seRechaza() {
        when(compraRepo.findWithLineasById(51L))
                .thenReturn(Optional.of(conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.PENDIENTE)));

        assertThatThrownBy(() -> service.revisarLinea(51L, 999L, RevisionLinea.DESCARTADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no es de esta compra");
    }

    /** PENDIENTE no es una decision: no se puede "volver a pendiente" por esta via. */
    @Test
    void revisar_conPendiente_seRechazaSinTocarLaBase() {
        assertThatThrownBy(() -> service.revisarLinea(51L, 2L, RevisionLinea.PENDIENTE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("aceptar o descartar");
        verify(compraRepo, never()).findWithLineasById(any());
    }

    @Test
    void revisar_compraQueNoExiste_seRechaza() {
        when(compraRepo.findWithLineasById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revisarLinea(99L, 2L, RevisionLinea.ACEPTADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    /** El listado trae las lineas a revisar aunque no traiga el detalle: son para la fila. */
    @Test
    void listado_traeLasLineasParaRevisarEnLaFila() {
        Compra compra = conUnaLineaParaRevisar(CompraEstado.EN_TRANSITO, RevisionLinea.PENDIENTE);

        CompraResponse r = CompraResponse.from(compra, false);

        assertThat(r.lineas()).isEmpty();
        assertThat(r.lineasEnRevision()).hasSize(1);
        assertThat(r.lineasEnRevision().get(0).id()).isEqualTo(2L);
        assertThat(r.lineasEnRevision().get(0).cantidad()).isEqualTo(1197421);
        assertThat(r.totalUnidades()).isEqualTo(2 + 1197421);   // pendiente todavia cuenta
    }
}
