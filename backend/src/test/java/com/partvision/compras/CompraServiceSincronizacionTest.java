package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.ResultadoSincronizacion.Tipo;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.CambiarEstadoRequest;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.inventory.service.StockService;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    /** El panel no se adelanta a la planilla: sin INGRESADA en la planilla no hay stock. */
    @Test
    void ingresarMientrasLaPlanillaDiceTransito_seRechaza() {
        when(compraRepo.findWithLineasById(7L))
                .thenReturn(Optional.of(guardada("EGSA", CompraEstado.EN_TRANSITO, "140000")));

        assertThatThrownBy(() -> service.marcarIngresada(7L, new CambiarEstadoRequest(
                List.of(new CambiarEstadoRequest.LineaUbicacion(1L, 50L)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EN TRANSITO");
        verify(stockService, never()).registrarEntrada(any());
    }
}
