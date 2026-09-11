package com.partvision.location.service;

import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.repository.MovimientoStockRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.location.domain.EstadoOcupacion;
import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.dto.UbicacionResponse;
import com.partvision.location.repository.UbicacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Buscar ubicaciones por producto y marcarlas ocupadas o libres. */
@ExtendWith(MockitoExtension.class)
class UbicacionServiceBusquedaTest {

    @Mock
    private UbicacionRepository ubicacionRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private MovimientoStockRepository movimientoStockRepository;
    @InjectMocks
    private UbicacionService ubicacionService;

    private static Ubicacion ubicacion() {
        Ubicacion u = new Ubicacion();
        u.setId(1L);
        u.setCodigo("A-01");
        u.setPath("A-01");
        u.setTipo(TipoUbicacion.ESTANTE);
        u.setEstadoOcupacion(EstadoOcupacion.LIBRE);
        return u;
    }

    @Test
    void buscarPorProducto_textoNulo_devuelveVacioSinConsultar() {
        assertThat(ubicacionService.buscarUbicacionesPorProducto(null)).isEmpty();
        verify(stockRepository, never()).findUbicacionIdsByProductoTexto(anyString());
    }

    @Test
    void buscarPorProducto_textoEnBlanco_devuelveVacioSinConsultar() {
        assertThat(ubicacionService.buscarUbicacionesPorProducto("   ")).isEmpty();
        verify(stockRepository, never()).findUbicacionIdsByProductoTexto(anyString());
    }

    @Test
    void buscarPorProducto_recortaElTextoAntesDeConsultar() {
        when(stockRepository.findUbicacionIdsByProductoTexto("filtro")).thenReturn(List.of(1L, 2L));

        assertThat(ubicacionService.buscarUbicacionesPorProducto("  filtro  ")).containsExactly(1L, 2L);
    }

    @Test
    void cambiarEstadoOcupacion_guardaElNuevoEstado() {
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(ubicacion()));
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> inv.getArgument(0));

        UbicacionResponse resp = ubicacionService.cambiarEstadoOcupacion(1L, EstadoOcupacion.LLENA);

        assertThat(resp.estadoOcupacion()).isEqualTo(EstadoOcupacion.LLENA);
    }

    @Test
    void cambiarEstadoOcupacion_ubicacionInexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.cambiarEstadoOcupacion(99L, EstadoOcupacion.LLENA))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
