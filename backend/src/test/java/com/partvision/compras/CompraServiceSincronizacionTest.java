package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.ResultadoSincronizacion.Tipo;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.domain.OrigenEstado;
import com.partvision.compras.dto.CambiarEstadoRequest;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.dto.SalidaRequest;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La planilla del cliente decide el estado de la compra: EN TRANSITO mientras la mercaderia
 * no llego, INGRESADA cuando llego. El flujo la reenvia seguido, asi que cada envio pone al
 * dia la compra en vez de duplicarla. El stock lo carga el panel, recien cuando llego.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceSincronizacionTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

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

    private static FacturaEntrante factura(String proveedor, CompraEstado estado, FacturaEntrante.Linea... lineas) {
        return new FacturaEntrante("900004113", FECHA, proveedor, estado, List.of(lineas));
    }

    private static FacturaEntrante.Linea linea(String codigo, int cantidad) {
        return new FacturaEntrante.Linea(codigo, "JUNTA TAPA CILINDRO", cantidad);
    }

    private static Compra guardada(String proveedor, CompraEstado estado, String... codigos) {
        Compra c = new Compra();
        c.setId(7L);
        c.setNumeroFactura("900004113");
        c.setFechaFactura(FECHA);
        c.setProveedor(proveedor);
        c.setEstado(estado);
        c.setCreatedAt(Instant.now());
        for (String codigo : codigos) {
            CompraLinea l = new CompraLinea();
            l.setCodigo(codigo);
            l.setDescripcion("JUNTA TAPA CILINDRO");
            l.setCantidad(1);
            c.addLinea(l);
        }
        return c;
    }

    private static Producto producto(Long id, String sku, String proveedor) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Producto " + sku);
        p.setEstado(ProductoEstado.ACTIVO);
        p.setProveedor(proveedor);
        return p;
    }

    // --- facturas nuevas ---

    @Test
    void nuevaEnTransito_seRegistraEnTransito() {
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(producto(10L, "JGPEP6T*AK", "EGSA")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("JGPEP6T*AK", 1), linea("IMPORTADOS", 2)));

        assertThat(r.tipo()).isEqualTo(Tipo.CREADA);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.EN_TRANSITO);
        assertThat(r.lineas()).isEqualTo(2);
        assertThat(r.lineasMatcheadas()).isEqualTo(1);
    }

    /** Si cuando llega por primera vez la planilla ya dice INGRESADA, nace por ubicar. */
    @Test
    void nuevaYaIngresada_naceParaUbicar() {
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.empty());
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.CREADA);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
    }

    // --- reenvios: la planilla manda el estado ---

    @Test
    void reenvioIgual_noHaceNada() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        verify(compraRepo, never()).save(any());
    }

    @Test
    void laPlanillaLaPasaAIngresada_quedaPorUbicar() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.ACTUALIZADA);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
        assertThat(r.mensaje()).contains("por ubicar");
        verify(compraRepo).save(any());
    }

    /** Una correccion en la planilla (la marcaron INGRESADA por error) se respeta. */
    @Test
    void laPlanillaLaVuelveATransito_antesDeUbicarla() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.POR_UBICAR, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.ACTUALIZADA);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.EN_TRANSITO);
    }

    /** El stock ya se cargo: volverla a transito dejaria stock de una compra que no llego. */
    @Test
    void laPlanillaLaVuelveATransito_despuesDeCargarElStock_esConflicto() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.INGRESADA, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.CONFLICTO);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.INGRESADA);
        assertThat(r.mensaje()).contains("ya se cargo");
        verify(compraRepo, never()).save(any());
    }

    @Test
    void yaIngresadaYLaPlanillaDiceIngresada_noHaceNada() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.INGRESADA, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    @Test
    void mismoNumeroConOtrasLineas_esConflictoYNoSeToca() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "JGPEP6T*AK")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("JGPEP6T*AK", 5)));

        assertThat(r.tipo()).isEqualTo(Tipo.CONFLICTO);
        assertThat(r.compra().getEstado()).isEqualTo(CompraEstado.EN_TRANSITO);
        verify(compraRepo, never()).save(any());
    }

    @Test
    void espaciosDeMasEnLaDescripcion_noSonUnCambio() {
        Compra existente = guardada("EGSA", CompraEstado.EN_TRANSITO);
        CompraLinea l = new CompraLinea();
        l.setCodigo("JGPEP6T*AK");
        l.setDescripcion("  JUNTA TAPA CILINDRO ");
        l.setCantidad(1);
        existente.addLinea(l);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(existente));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("JGPEP6T*AK", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
    }

    // --- el proveedor llega despues ---

    /**
     * La planilla todavia no tiene columna de proveedor. Cuando la agreguen, las facturas que
     * ya estaban se completan en vez de chocar, y los SKU repetidos se pueden resolver.
     */
    @Test
    void proveedorQueAntesNoEstaba_seCompletaYResuelveLosRepetidos() {
        Compra existente = guardada(null, CompraEstado.EN_TRANSITO, "140000");
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(existente));
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.ACTUALIZADA);
        assertThat(r.compra().getProveedor()).isEqualTo("EGSA");
        assertThat(r.compra().getLineas().get(0).getProducto().getId()).isEqualTo(245649L);
        assertThat(r.lineasMatcheadas()).isEqualTo(1);
        assertThat(r.mensaje()).contains("se completo el proveedor");
    }

    @Test
    void proveedorYEstadoCambianJuntos_informaAmbos() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada(null, CompraEstado.EN_TRANSITO, "140000")));
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of());

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("140000", 1)));

        assertThat(r.mensaje()).isEqualTo("se completo el proveedor; llego: queda por ubicar");
    }

    /** Con el stock ya cargado se guarda el proveedor, pero no se tocan los productos. */
    @Test
    void proveedorQueAntesNoEstaba_enUnaYaIngresada_noReasignaProductos() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada(null, CompraEstado.INGRESADA, "140000")));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.ACTUALIZADA);
        assertThat(r.compra().getProveedor()).isEqualTo("EGSA");
        verify(productoRepo, never()).findBySkuIn(any());
    }

    /** Que falte el proveedor en el reenvio no contradice al que ya estaba. */
    @Test
    void proveedorQueFaltaEnElReenvio_noEsConflicto() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "140000")));

        ResultadoSincronizacion r = service.sincronizar(
                factura(null, CompraEstado.EN_TRANSITO, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        assertThat(r.compra().getProveedor()).isEqualTo("EGSA");
    }

    /**
     * Un importado que alguien asocio a mano no se pierde cuando llega el proveedor: buscar
     * de nuevo el codigo IMPORTADOS no encontraria nada y le borraria el producto.
     */
    @Test
    void proveedorQueLlegaDespues_noDesasociaUnImportadoResueltoAMano() {
        Compra existente = guardada(null, CompraEstado.EN_TRANSITO, "IMPORTADOS", "140000");
        Producto aMano = producto(900L, "IMP-00001", null);
        existente.getLineas().get(0).setProducto(aMano);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(existente));
        when(productoRepo.findBySkuIn(any())).thenReturn(List.of(
                producto(279402L, "140000", "Autopartes del Sur"),
                producto(245649L, "140000", "EGSA")));

        ResultadoSincronizacion r = service.sincronizar(factura("EGSA", CompraEstado.EN_TRANSITO,
                linea("IMPORTADOS", 1), linea("140000", 1)));

        assertThat(r.compra().getLineas().get(0).getProducto()).isSameAs(aMano);
        assertThat(r.compra().getLineas().get(1).getProducto().getId()).isEqualTo(245649L);
        assertThat(r.lineasMatcheadas()).isEqualTo(2);
    }

    /** Mientras la planilla no tenga columna de proveedor, los reenvios no cambian nada. */
    @Test
    void sinProveedorDeNingunLado_noHayNadaQueCompletar() {
        when(compraRepo.findByNumeroFactura("900004113"))
                .thenReturn(Optional.of(guardada(null, CompraEstado.EN_TRANSITO, "140000")));

        ResultadoSincronizacion r = service.sincronizar(
                factura(null, CompraEstado.EN_TRANSITO, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        verify(productoRepo, never()).findBySkuIn(any());
    }

    // --- ingresar desde el panel ---

    /**
     * El panel puede adelantarse a la planilla: si la mercaderia esta en el deposito, esperar
     * a que el cliente actualice la celda solo retrasa el stock. Queda marcado como PANEL.
     */
    @Test
    void ingresarMientrasLaPlanillaDiceTransito_cargaElStockYQuedaComoPanel() {
        Compra compra = guardada("EGSA", CompraEstado.EN_TRANSITO, "140000");
        compra.getLineas().get(0).setId(1L);
        compra.getLineas().get(0).setProducto(producto(10L, "140000", "EGSA"));
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra));
        when(ubicacionService.getEntity(50L)).thenReturn(ubicacion(50L, "A-01-01"));

        service.marcarIngresada(7L, new CambiarEstadoRequest(
                List.of(new CambiarEstadoRequest.LineaUbicacion(1L, 50L))));

        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
        assertThat(compra.getEstadoOrigen()).isEqualTo(OrigenEstado.PANEL);
        verify(stockService).registrarEntrada(any());
    }

    // --- la planilla contra lo que hicimos a mano ---

    /**
     * Lo que mas importa: la planilla sigue diciendo INGRESADA en cada envio, y el stock ya
     * esta cargado. Recibirla de nuevo no puede volver a cargarlo.
     */
    @Test
    void planillaDiceIngresada_conElStockYaCargado_noVuelveACargarlo() {
        Compra compra = guardada("EGSA", CompraEstado.INGRESADA, "140000");
        compra.setEstadoOrigen(OrigenEstado.PANEL);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(compra));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
        verify(stockService, never()).registrarEntrada(any());
        verify(compraRepo, never()).save(any(Compra.class));
    }

    /**
     * Nos adelantamos a la planilla: la diferencia es a proposito, asi que no es conflicto.
     * Marcarla como tal mandaria un aviso en cada corrida del flujo hasta que el cliente
     * actualice la celda.
     */
    @Test
    void planillaDiceTransito_peroLoIngresamosNosotros_noEsConflicto() {
        Compra compra = guardada("EGSA", CompraEstado.INGRESADA, "140000");
        compra.setEstadoOrigen(OrigenEstado.PANEL);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(compra));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.SIN_CAMBIOS);
        assertThat(r.mensaje()).contains("desde el panel");
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    /**
     * En cambio, si la planilla misma habia dicho INGRESADA y ahora vuelve atras con el stock
     * ya cargado, alguien la edito hacia atras: eso si hay que avisarlo.
     */
    @Test
    void planillaVuelveAtrasLoQueElllaMismaDijo_siEsConflicto() {
        Compra compra = guardada("EGSA", CompraEstado.INGRESADA, "140000");
        compra.setEstadoOrigen(OrigenEstado.PLANILLA);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(compra));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.EN_TRANSITO, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.CONFLICTO);
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    /** Un cambio de estado que viene de la planilla queda anotado como tal. */
    @Test
    void cambioDeEstadoDeLaPlanilla_quedaMarcadoComoPlanilla() {
        Compra compra = guardada("EGSA", CompraEstado.EN_TRANSITO, "140000");
        compra.setEstadoOrigen(OrigenEstado.PANEL);
        when(compraRepo.findByNumeroFactura("900004113")).thenReturn(Optional.of(compra));

        ResultadoSincronizacion r = service.sincronizar(
                factura("EGSA", CompraEstado.POR_UBICAR, linea("140000", 1)));

        assertThat(r.tipo()).isEqualTo(Tipo.ACTUALIZADA);
        assertThat(compra.getEstadoOrigen()).isEqualTo(OrigenEstado.PLANILLA);
    }

    // --- cambiar el estado a mano y revertir ---

    @Test
    void cambiarEstado_aTransito_noTocaElStock() {
        Compra compra = guardada("EGSA", CompraEstado.POR_UBICAR, "140000");
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra));

        service.cambiarEstado(7L, CompraEstado.EN_TRANSITO);

        assertThat(compra.getEstado()).isEqualTo(CompraEstado.EN_TRANSITO);
        assertThat(compra.getEstadoOrigen()).isEqualTo(OrigenEstado.PANEL);
        verify(stockService, never()).registrarSalida(any());
    }

    /** Revertir un ingreso devuelve lo que habia cargado, ubicacion por ubicacion. */
    @Test
    void cambiarEstado_revirtiendoUnIngreso_devuelveElStock() {
        Compra compra = ingresadaConStock();
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra));

        service.cambiarEstado(7L, CompraEstado.POR_UBICAR);

        ArgumentCaptor<SalidaRequest> salida = ArgumentCaptor.forClass(SalidaRequest.class);
        verify(stockService).registrarSalida(salida.capture());
        assertThat(salida.getValue().productoId()).isEqualTo(10L);
        assertThat(salida.getValue().ubicacionId()).isEqualTo(50L);
        assertThat(salida.getValue().cantidad()).isEqualTo(1);
        assertThat(salida.getValue().motivo()).contains("900004113");
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
        assertThat(compra.getLineas().get(0).getUbicacionIngreso()).isNull();
    }

    /** Si la mercaderia ya no esta, no se revierte nada: mejor eso que stock en negativo. */
    @Test
    void cambiarEstado_revirtiendoSinStock_falla() {
        Compra compra = ingresadaConStock();
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra));
        doThrow(new BusinessException("Stock insuficiente: disponible 0, solicitado 1"))
                .when(stockService).registrarSalida(any());

        assertThatThrownBy(() -> service.cambiarEstado(7L, CompraEstado.POR_UBICAR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No se puede revertir")
                .hasMessageContaining("A-01-01");
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    /** Una linea sin producto o sin ubicacion nunca cargo stock: no hay nada que devolver. */
    @Test
    void cambiarEstado_revirtiendoLineasQueNoCargaronStock_noRegistraSalidas() {
        Compra compra = guardada("EGSA", CompraEstado.INGRESADA, "IMPORTADOS");
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra));

        service.cambiarEstado(7L, CompraEstado.POR_UBICAR);

        verify(stockService, never()).registrarSalida(any());
        assertThat(compra.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
    }

    @Test
    void cambiarEstado_aIngresada_seRechaza() {
        assertThatThrownBy(() -> service.cambiarEstado(7L, CompraEstado.INGRESADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ubicacion");
        verify(compraRepo, never()).findWithLineasById(any());
    }

    @Test
    void cambiarEstado_alMismoQueTiene_seRechaza() {
        when(compraRepo.findWithLineasById(7L))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "140000")));

        assertThatThrownBy(() -> service.cambiarEstado(7L, CompraEstado.EN_TRANSITO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya esta en ese estado");
    }

    @Test
    void cambiarEstado_deUnaCompraQueNoExiste_seRechaza() {
        when(compraRepo.findWithLineasById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(99L, CompraEstado.EN_TRANSITO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    private static Compra ingresadaConStock() {
        Compra compra = guardada("EGSA", CompraEstado.INGRESADA, "140000");
        compra.setEstadoOrigen(OrigenEstado.PANEL);
        CompraLinea linea = compra.getLineas().get(0);
        linea.setId(1L);
        linea.setProducto(producto(10L, "140000", "EGSA"));
        linea.setUbicacionIngreso(ubicacion(50L, "A-01-01"));
        return compra;
    }

    private static Ubicacion ubicacion(Long id, String codigo) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setCodigo(codigo);
        return u;
    }
}
