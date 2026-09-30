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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una compra se ingresa por partes. Hasta el 2026-09-30 bastaba ubicar una sola linea para que
 * la compra entera quedara INGRESADA: la factura 0018-00006900 decia "Ingresada, 25 unidades"
 * con 12 en el stock, y las otras 4 lineas no se podian ingresar mas sin revertir todo.
 */
@ExtendWith(MockitoExtension.class)
class CompraServiceIngresoParcialTest {

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
        lenient().when(compraRepo.save(any(Compra.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ubicacionService.getEntity(50L)).thenReturn(ubicacion(50L, "020302"));
        lenient().when(ubicacionService.getEntity(60L)).thenReturn(ubicacion(60L, "DEPOSITO INTERNO"));
    }

    /** Como la 0018-00006900: lineas con articulo, cantidades 12, 1 y 5. */
    private static Compra compra(CompraEstado estado, Long... productoIds) {
        Compra c = new Compra();
        c.setId(7L);
        c.setNumeroFactura("0018-00006900");
        c.setFechaFactura(LocalDate.of(2026, 9, 24));
        c.setProveedor("EGSA");
        c.setEstado(estado);
        c.setEstadoPlanilla(estado == CompraEstado.INGRESADA ? CompraEstado.POR_UBICAR : estado);
        c.setCreatedAt(Instant.now());
        int[] cantidades = {12, 1, 5};
        for (int i = 0; i < productoIds.length; i++) {
            CompraLinea l = new CompraLinea();
            l.setId(100L + i);
            l.setCodigo("COD" + i);
            l.setDescripcion("linea " + i);
            l.setCantidad(cantidades[i]);
            if (productoIds[i] != null) {
                Producto p = new Producto();
                p.setId(productoIds[i]);
                p.setSku("SKU" + i);
                p.setDescripcion("producto " + i);
                p.setEstado(ProductoEstado.ACTIVO);
                l.setProducto(p);
            }
            c.addLinea(l);
        }
        return c;
    }

    private static Ubicacion ubicacion(Long id, String codigo) {
        Ubicacion u = new Ubicacion();
        u.setId(id);
        u.setCodigo(codigo);
        return u;
    }

    private static CambiarEstadoRequest ubicar(long... lineaYUbicacion) {
        CambiarEstadoRequest.LineaUbicacion[] a = new CambiarEstadoRequest.LineaUbicacion[lineaYUbicacion.length / 2];
        for (int i = 0; i < a.length; i++) {
            a[i] = new CambiarEstadoRequest.LineaUbicacion(lineaYUbicacion[2 * i], lineaYUbicacion[2 * i + 1]);
        }
        return new CambiarEstadoRequest(Arrays.asList(a));
    }

    private static void yaUbicada(CompraLinea linea, Long ubicacionId) {
        linea.setUbicacionIngreso(ubicacion(ubicacionId, "020302"));
    }

    // --- ingresar por partes ---

    /** El caso reportado: se ubica 1 de 3. La compra sigue por ubicar, con lo real a la vista. */
    @Test
    void ubicarUnaDeTres_quedaPorUbicarConElAvance() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L, 3L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        CompraResponse r = service.marcarIngresada(7L, ubicar(100L, 50L));

        assertThat(c.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
        verify(stockService, times(1)).registrarEntrada(any());
        assertThat(r.lineasEnStock()).isEqualTo(1);
        assertThat(r.lineasPorUbicar()).isEqualTo(2);
        assertThat(r.unidadesEnStock()).isEqualTo(12);
        assertThat(r.totalUnidades()).isEqualTo(18);
    }

    /** La segunda pasada termina: recien ahi queda INGRESADA. */
    @Test
    void ubicarLasQueFaltan_quedaIngresada() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L, 3L);
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        CompraResponse r = service.marcarIngresada(7L, ubicar(101L, 60L, 102L, 60L));

        assertThat(c.getEstado()).isEqualTo(CompraEstado.INGRESADA);
        assertThat(r.lineasPorUbicar()).isZero();
        assertThat(r.unidadesEnStock()).isEqualTo(18);
    }

    /**
     * Lo mas importante de la segunda pasada: la linea que ya entro no se vuelve a cargar,
     * aunque el panel la mande de nuevo con su ubicacion.
     */
    @Test
    void lineaYaUbicada_noSeCargaDosVeces() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L, 3L);
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.marcarIngresada(7L, ubicar(100L, 60L, 101L, 60L));

        ArgumentCaptor<EntradaRequest> entrada = ArgumentCaptor.forClass(EntradaRequest.class);
        verify(stockService, times(1)).registrarEntrada(entrada.capture());
        assertThat(entrada.getValue().productoId()).isEqualTo(2L);
        assertThat(c.getLineas().get(0).getUbicacionIngreso().getId()).isEqualTo(50L);   // no se movio
    }

    /** Ubicar algo significa que llego: una compra en transito pasa a por ubicar. */
    @Test
    void ubicarUnaParteEstandoEnTransito_pasaAPorUbicar() {
        Compra c = compra(CompraEstado.EN_TRANSITO, 1L, 2L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.marcarIngresada(7L, ubicar(100L, 50L));

        assertThat(c.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
    }

    /**
     * Una linea sin articulo (un importado sin resolver) no tiene a que producto cargarle stock:
     * no se ubica, y no impide cerrar la compra.
     */
    @Test
    void lineaSinArticulo_noSeUbicaNiBloquea() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, null);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.marcarIngresada(7L, ubicar(100L, 50L, 101L, 50L));

        assertThat(c.getLineas().get(1).getUbicacionIngreso()).isNull();
        verify(stockService, times(1)).registrarEntrada(any());
        assertThat(c.getEstado()).isEqualTo(CompraEstado.INGRESADA);
    }

    /** Si no queda nada que ubicar (solo importados sin resolver), se cierra sin asignaciones. */
    @Test
    void sinNadaQueUbicar_seCierraConLaListaVacia() {
        Compra c = compra(CompraEstado.POR_UBICAR, (Long) null);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.marcarIngresada(7L, new CambiarEstadoRequest(List.of()));

        assertThat(c.getEstado()).isEqualTo(CompraEstado.INGRESADA);
        verify(stockService, never()).registrarEntrada(any());
    }

    @Test
    void conLineasPorUbicar_laListaVaciaSeRechaza() {
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra(CompraEstado.POR_UBICAR, 1L, 2L)));

        assertThatThrownBy(() -> service.marcarIngresada(7L, new CambiarEstadoRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 linea(s) que faltan");
    }

    // --- lo que tiene que proteger el stock cargado a medias ---

    /** La planilla vuelve atras sobre una compra a medias: hay stock cargado, es conflicto. */
    @Test
    void planillaVuelveAtras_conStockCargadoAMedias_esConflicto() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L);
        c.getLineas().forEach(l -> l.setDescripcion("junta"));
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findByNumeroFactura("0018-00006900")).thenReturn(Optional.of(c));

        ResultadoSincronizacion r = service.sincronizar(new FacturaEntrante("0018-00006900",
                LocalDate.of(2026, 9, 24), "EGSA", CompraEstado.EN_TRANSITO, List.of(
                        new FacturaEntrante.Linea("COD0", "junta", 12),
                        new FacturaEntrante.Linea("COD1", "junta", 1))));

        assertThat(r.tipo()).isEqualTo(Tipo.CONFLICTO);
        assertThat(c.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
    }

    /** Volver a EN TRANSITO una compra a medias devuelve lo que ya se habia ubicado. */
    @Test
    void volverATransito_conStockAMedias_devuelveLoUbicado() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L);
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.cambiarEstado(7L, CompraEstado.EN_TRANSITO);

        ArgumentCaptor<SalidaRequest> salida = ArgumentCaptor.forClass(SalidaRequest.class);
        verify(stockService, times(1)).registrarSalida(salida.capture());
        assertThat(salida.getValue().cantidad()).isEqualTo(12);
        assertThat(c.getEstado()).isEqualTo(CompraEstado.EN_TRANSITO);
        assertThat(c.tieneStockCargado()).isFalse();
    }

    /** Revertir lo ubicado sin cambiar de estado: por ubicar, a por ubicar, sin nada en stock. */
    @Test
    void revertirLoUbicado_quedandoPorUbicar() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L);
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        service.cambiarEstado(7L, CompraEstado.POR_UBICAR);

        verify(stockService, times(1)).registrarSalida(any());
        assertThat(c.getEstado()).isEqualTo(CompraEstado.POR_UBICAR);
        assertThat(c.lineasPorUbicar()).isEqualTo(2);
    }

    /** Sin stock cargado, pedir el mismo estado sigue siendo un error. */
    @Test
    void mismoEstadoSinStock_seRechaza() {
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(compra(CompraEstado.POR_UBICAR, 1L)));

        assertThatThrownBy(() -> service.cambiarEstado(7L, CompraEstado.POR_UBICAR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya esta en ese estado");
    }

    /** Una linea que ya entro al stock no cambia de decision: el stock se cargo con esa. */
    @Test
    void revisarUnaLineaQueYaEntro_seRechaza() {
        Compra c = compra(CompraEstado.POR_UBICAR, 1L, 2L);
        c.getLineas().get(0).setRevision(RevisionLinea.ACEPTADA);
        yaUbicada(c.getLineas().get(0), 50L);
        when(compraRepo.findWithLineasById(7L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.revisarLinea(7L, 100L, RevisionLinea.DESCARTADA))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya entro al stock");
    }
}
