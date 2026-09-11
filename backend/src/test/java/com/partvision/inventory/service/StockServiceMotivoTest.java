package com.partvision.inventory.service;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.service.ProductoService;
import com.partvision.inventory.domain.MovimientoStock;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.dto.ConteoRequest;
import com.partvision.inventory.dto.ConteoResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** El motivo que queda asentado en el ajuste de un conteo fisico. */
@ExtendWith(MockitoExtension.class)
class StockServiceMotivoTest {

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

    private ConteoResponse contar(String motivo) {
        Ubicacion u = Ubicacion.builder().id(10L).tipo(TipoUbicacion.ESTANTE)
                .codigo("A").path("A").activo(true).build();
        when(ubicacionService.getEntity(10L)).thenReturn(u);
        when(stockRepository.lockByProductoAndUbicacion(1L, 10L)).thenReturn(Optional.of(
                Stock.builder().id(10L).producto(producto).ubicacion(u).cantidad(8).build()));

        return service.registrarConteo(new ConteoRequest(1L, 10L, 12, motivo));
    }

    @Test
    void motivoPropio_seRespeta() {
        assertThat(contar("Recuento anual").movimiento().motivo()).isEqualTo("Recuento anual");
    }

    /** Un motivo en blanco no puede dejar el movimiento sin explicacion en el historial. */
    @Test
    void motivoEnBlanco_usaElPorDefecto() {
        assertThat(contar("   ").movimiento().motivo()).isEqualTo("Conteo fisico");
    }
}
