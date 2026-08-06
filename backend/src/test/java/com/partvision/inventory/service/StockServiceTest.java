package com.partvision.inventory.service;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.inventory.domain.MovimientoStock;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.inventory.dto.AjusteRequest;
import com.partvision.inventory.dto.ConteoLoteRequest;
import com.partvision.inventory.dto.ConteoRequest;
import com.partvision.inventory.dto.ConteoResponse;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.dto.SalidaRequest;
import com.partvision.inventory.dto.StockResumenResponse;
import com.partvision.inventory.dto.TransferenciaRequest;
import com.partvision.inventory.repository.MovimientoStockRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private MovimientoStockRepository movimientoRepository;
    @Mock private ProductoService productoService;
    @Mock private UbicacionService ubicacionService;

    private StockService service;

    private final Producto producto = Producto.builder().id(1L).descripcion("Filtro").build();

    @BeforeEach
    void setUp() {
        service = new StockService(stockRepository, movimientoRepository, productoService, ubicacionService);
        lenient().when(productoService.getEntity(1L)).thenReturn(producto);
        lenient().when(movimientoRepository.save(any(MovimientoStock.class))).thenAnswer(inv -> {
            MovimientoStock m = inv.getArgument(0);
            m.setId(100L);
            return m;
        });
        lenient().when(stockRepository.save(any(Stock.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Ubicacion ubicacion(long id, String codigo) {
        return Ubicacion.builder().id(id).tipo(TipoUbicacion.ESTANTE).codigo(codigo).path(codigo).activo(true).build();
    }

    private Stock stock(Ubicacion u, int cantidad) {
        return Stock.builder().id(u.getId()).producto(producto).ubicacion(u).cantidad(cantidad).build();
    }

    // ---------- ENTRADA ----------

    @Test
    void entrada_sobreStockExistente_suma() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 8)));

        MovimientoResponse r = service.registrarEntrada(new EntradaRequest(1L, 10L, 5, "compra"));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(r.cantidad()).isEqualTo(5);
        assertThat(r.ubicacionDestinoId()).isEqualTo(10L);
    }

    @Test
    void entrada_creaStockSiNoExiste() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.empty());

        MovimientoResponse r = service.registrarEntrada(new EntradaRequest(1L, 10L, 5, null));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.ENTRADA);
    }

    // ---------- SALIDA ----------

    @Test
    void salida_conStockSuficiente_descuenta() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 8)));

        MovimientoResponse r = service.registrarSalida(new SalidaRequest(1L, 10L, 3, "venta"));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(r.ubicacionOrigenId()).isEqualTo(10L);
    }

    @Test
    void salida_sinStockEnUbicacion_lanza422() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarSalida(new SalidaRequest(1L, 10L, 3, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void salida_stockInsuficiente_lanza422() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 2)));

        assertThatThrownBy(() -> service.registrarSalida(new SalidaRequest(1L, 10L, 5, null)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- AJUSTES ----------

    @Test
    void ajustePositivo_suma() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 1)));

        MovimientoResponse r = service.ajustar(
                new AjusteRequest(1L, 10L, TipoMovimiento.AJUSTE_POSITIVO, 4, "recuento"));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.AJUSTE_POSITIVO);
    }

    @Test
    void ajusteNegativo_descuenta() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 6)));

        MovimientoResponse r = service.ajustar(
                new AjusteRequest(1L, 10L, TipoMovimiento.AJUSTE_NEGATIVO, 2, "rotura"));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.AJUSTE_NEGATIVO);
    }

    @Test
    void ajusteNegativo_insuficiente_lanza422() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 1)));

        assertThatThrownBy(() -> service.ajustar(
                new AjusteRequest(1L, 10L, TipoMovimiento.AJUSTE_NEGATIVO, 5, "rotura")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ajuste_tipoNoAjuste_lanza422() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);

        assertThatThrownBy(() -> service.ajustar(
                new AjusteRequest(1L, 10L, TipoMovimiento.ENTRADA, 5, "x")))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- CONTEO ----------

    @Test
    void conteo_masQueLoRegistrado_generaAjustePositivo() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 8)));

        ConteoResponse r = service.registrarConteo(new ConteoRequest(1L, 10L, 12, null));

        assertThat(r.cantidadAnterior()).isEqualTo(8);
        assertThat(r.cantidadNueva()).isEqualTo(12);
        assertThat(r.movimiento().tipo()).isEqualTo(TipoMovimiento.AJUSTE_POSITIVO);
        assertThat(r.movimiento().cantidad()).isEqualTo(4);
        assertThat(r.movimiento().motivo()).isEqualTo("Conteo fisico");
    }

    @Test
    void conteo_menosQueLoRegistrado_generaAjusteNegativo() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 10)));

        ConteoResponse r = service.registrarConteo(new ConteoRequest(1L, 10L, 3, "recuento anual"));

        assertThat(r.cantidadNueva()).isEqualTo(3);
        assertThat(r.movimiento().tipo()).isEqualTo(TipoMovimiento.AJUSTE_NEGATIVO);
        assertThat(r.movimiento().cantidad()).isEqualTo(7);
        assertThat(r.movimiento().motivo()).isEqualTo("recuento anual");
    }

    @Test
    void conteo_igualALoRegistrado_noGeneraMovimiento() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(u, 5)));

        ConteoResponse r = service.registrarConteo(new ConteoRequest(1L, 10L, 5, null));

        assertThat(r.cantidadNueva()).isEqualTo(5);
        assertThat(r.movimiento()).isNull();
    }

    @Test
    void conteo_sinStockPrevio_creaYAjusta() {
        Ubicacion u = ubicacion(10L, "A");
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.empty());

        ConteoResponse r = service.registrarConteo(new ConteoRequest(1L, 10L, 15, null));

        assertThat(r.cantidadAnterior()).isZero();
        assertThat(r.cantidadNueva()).isEqualTo(15);
        assertThat(r.movimiento().tipo()).isEqualTo(TipoMovimiento.AJUSTE_POSITIVO);
        assertThat(r.movimiento().cantidad()).isEqualTo(15);
    }

    @Test
    void conteoLote_procesaTodasLasLineas() {
        Ubicacion a = ubicacion(10L, "A");
        Ubicacion b = ubicacion(11L, "B");
        when(ubicacionService.getEntity(10L)).thenReturn(a);
        when(ubicacionService.getEntity(11L)).thenReturn(b);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(stock(a, 2)));
        when(stockRepository.lockByProductoAndUbicacion(1L, 11L)).thenReturn(Optional.empty());

        List<ConteoResponse> r = service.registrarConteoLote(new ConteoLoteRequest(List.of(
                new ConteoRequest(1L, 10L, 5, null),
                new ConteoRequest(1L, 11L, 9, null))));

        assertThat(r).hasSize(2);
        assertThat(r.get(0).cantidadNueva()).isEqualTo(5);
        assertThat(r.get(1).cantidadNueva()).isEqualTo(9);
    }

    // ---------- TRANSFERENCIA ----------

    @Test
    void transferencia_origenMenorQueDestino() {
        Ubicacion origen = ubicacion(1L, "A");
        Ubicacion destino = ubicacion(2L, "B");
        when(ubicacionService.getEntity(1L)).thenReturn(origen);
        when(ubicacionService.getEntity(2L)).thenReturn(destino);
        when(stockRepository.lockByProductoAndUbicacion(1L, 1L)).thenReturn(Optional.of(stock(origen, 10)));
        when(stockRepository.lockByProductoAndUbicacion(1L, 2L)).thenReturn(Optional.of(stock(destino, 0)));

        MovimientoResponse r = service.transferir(new TransferenciaRequest(1L, 1L, 2L, 3, "reubicacion"));

        assertThat(r.tipo()).isEqualTo(TipoMovimiento.TRANSFERENCIA);
        assertThat(r.ubicacionOrigenId()).isEqualTo(1L);
        assertThat(r.ubicacionDestinoId()).isEqualTo(2L);
    }

    @Test
    void transferencia_destinoMenorQueOrigen() {
        Ubicacion origen = ubicacion(5L, "E");
        Ubicacion destino = ubicacion(3L, "C");
        when(ubicacionService.getEntity(5L)).thenReturn(origen);
        when(ubicacionService.getEntity(3L)).thenReturn(destino);
        when(stockRepository.lockByProductoAndUbicacion(1L, 5L)).thenReturn(Optional.of(stock(origen, 10)));
        when(stockRepository.lockByProductoAndUbicacion(1L, 3L)).thenReturn(Optional.empty());

        MovimientoResponse r = service.transferir(new TransferenciaRequest(1L, 5L, 3L, 4, null));

        assertThat(r.ubicacionDestinoId()).isEqualTo(3L);
    }

    @Test
    void transferencia_mismaUbicacion_lanza422() {
        assertThatThrownBy(() -> service.transferir(new TransferenciaRequest(1L, 7L, 7L, 1, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void transferencia_origenInsuficiente_lanza422() {
        Ubicacion origen = ubicacion(1L, "A");
        Ubicacion destino = ubicacion(2L, "B");
        when(ubicacionService.getEntity(1L)).thenReturn(origen);
        when(ubicacionService.getEntity(2L)).thenReturn(destino);
        when(stockRepository.lockByProductoAndUbicacion(1L, 1L)).thenReturn(Optional.of(stock(origen, 1)));
        when(stockRepository.lockByProductoAndUbicacion(1L, 2L)).thenReturn(Optional.of(stock(destino, 0)));

        assertThatThrownBy(() -> service.transferir(new TransferenciaRequest(1L, 1L, 2L, 5, null)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- CONSULTAS ----------

    @Test
    void getResumen_sumaTotalYDesglosa() {
        Ubicacion a = ubicacion(1L, "A");
        Ubicacion b = ubicacion(2L, "B");
        when(stockRepository.findByProductoId(1L)).thenReturn(List.of(stock(a, 20), stock(b, 15)));

        StockResumenResponse r = service.getResumen(1L);

        assertThat(r.total()).isEqualTo(35);
        assertThat(r.ubicaciones()).hasSize(2);
    }

    @Test
    void listarMovimientos_devuelvePagina() {
        MovimientoStock m = MovimientoStock.builder()
                .id(1L).producto(producto).tipo(TipoMovimiento.ENTRADA).cantidad(5).build();
        when(movimientoRepository.findByProductoIdOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(m)));

        var page = service.listarMovimientos(1L, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(MovimientoResponse::tipo)
                .containsExactly(TipoMovimiento.ENTRADA);
    }
}
