package com.partvision.location.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.repository.MovimientoStockRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.dto.StockDetalleUbicacionResponse;
import com.partvision.location.dto.StockPorUbicacionResponse;
import com.partvision.location.dto.UbicacionRequest;
import com.partvision.location.dto.UbicacionResponse;
import com.partvision.location.repository.UbicacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UbicacionServiceTest {

    @Mock
    private UbicacionRepository ubicacionRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private MovimientoStockRepository movimientoStockRepository;
    @InjectMocks
    private UbicacionService ubicacionService;

    @Test
    void create_raiz_usaCodigoComoPath() {
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> {
            Ubicacion u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UbicacionResponse response = ubicacionService.create(
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "A", null));

        assertThat(response.path()).isEqualTo("A");
        assertThat(response.parentId()).isNull();
        assertThat(response.activo()).isTrue();
    }

    @Test
    void create_conPadre_concatenaPath() {
        Ubicacion deposito = Ubicacion.builder().id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(deposito));
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> {
            Ubicacion u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        UbicacionResponse response = ubicacionService.create(
                new UbicacionRequest(TipoUbicacion.PASILLO, "1", 1L));

        assertThat(response.path()).isEqualTo("A/1");
        assertThat(response.parentId()).isEqualTo(1L);
    }

    @Test
    void create_padreInexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.create(new UbicacionRequest(TipoUbicacion.PASILLO, "1", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_jerarquiaInvertida_lanza422() {
        Ubicacion cajon = Ubicacion.builder().id(1L).tipo(TipoUbicacion.CAJON).codigo("2").path("A/1/2").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(cajon));

        assertThatThrownBy(() -> ubicacionService.create(new UbicacionRequest(TipoUbicacion.DEPOSITO, "B", 1L)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void create_codigoDuplicado_lanza409() {
        Ubicacion existente = Ubicacion.builder().id(5L).codigo("A").build();
        when(ubicacionRepository.findByCodigoIgnoreCase("A")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> ubicacionService.create(
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "A", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void findById_existente() {
        Ubicacion u = Ubicacion.builder().id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").activo(true).build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThat(ubicacionService.findById(1L).codigo()).isEqualTo("A");
    }

    @Test
    void findById_inexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findRaices_devuelveSinPadre() {
        when(ubicacionRepository.findByParentIsNullOrderByCodigo()).thenReturn(List.of(
                Ubicacion.builder().id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").build()));

        assertThat(ubicacionService.findRaices()).extracting(UbicacionResponse::codigo).containsExactly("A");
    }

    @Test
    void findHijos_devuelveDirectos() {
        Ubicacion padre = Ubicacion.builder().id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(padre));
        when(ubicacionRepository.findByParentIdOrderByCodigo(1L)).thenReturn(List.of(
                Ubicacion.builder().id(2L).tipo(TipoUbicacion.PASILLO).codigo("1").path("A/1").parent(padre).build()));

        assertThat(ubicacionService.findHijos(1L)).extracting(UbicacionResponse::parentId).containsExactly(1L);
    }

    @Test
    void findHijos_padreInexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.findHijos(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // --- tests nuevos para cubrir update, delete, getByCodigo, stockResumen, stockDetalle ---

    @Test
    void update_raiz_actualizaCodigoYPath() {
        Ubicacion existente = Ubicacion.builder()
                .id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").activo(true).build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ubicacionRepository.findByCodigoIgnoreCase("B")).thenReturn(Optional.empty());
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> inv.getArgument(0));

        UbicacionResponse r = ubicacionService.update(1L,
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "B", "Deposito principal", null));

        assertThat(r.codigo()).isEqualTo("B");
        assertThat(r.path()).isEqualTo("B");
        assertThat(r.descripcion()).isEqualTo("Deposito principal");
    }

    @Test
    void update_conPadre_noModificaPath() {
        Ubicacion padre = Ubicacion.builder().id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").build();
        Ubicacion hijo = Ubicacion.builder()
                .id(2L).tipo(TipoUbicacion.PASILLO).codigo("1").path("A/1").parent(padre).activo(true).build();
        when(ubicacionRepository.findById(2L)).thenReturn(Optional.of(hijo));
        when(ubicacionRepository.findByCodigoIgnoreCase("2")).thenReturn(Optional.empty());
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> inv.getArgument(0));

        UbicacionResponse r = ubicacionService.update(2L,
                new UbicacionRequest(null, "2", null));

        assertThat(r.codigo()).isEqualTo("2");
        assertThat(r.path()).isEqualTo("A/1");
    }

    @Test
    void update_codigoDuplicadoEnOtra_lanza409() {
        Ubicacion existente = Ubicacion.builder()
                .id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").activo(true).build();
        Ubicacion otra = Ubicacion.builder().id(5L).codigo("B").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ubicacionRepository.findByCodigoIgnoreCase("B")).thenReturn(Optional.of(otra));

        assertThatThrownBy(() -> ubicacionService.update(1L,
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "B", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_mismoCodigoMismoId_noDuplica() {
        Ubicacion existente = Ubicacion.builder()
                .id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").activo(true).build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ubicacionRepository.findByCodigoIgnoreCase("A")).thenReturn(Optional.of(existente));
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> inv.getArgument(0));

        UbicacionResponse r = ubicacionService.update(1L,
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "A", null));

        assertThat(r.codigo()).isEqualTo("A");
    }

    @Test
    void update_tipoNull_conservaTipoExistente() {
        Ubicacion existente = Ubicacion.builder()
                .id(1L).tipo(TipoUbicacion.DEPOSITO).codigo("A").path("A").activo(true).build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ubicacionRepository.findByCodigoIgnoreCase("A")).thenReturn(Optional.of(existente));
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> inv.getArgument(0));

        UbicacionResponse r = ubicacionService.update(1L,
                new UbicacionRequest(null, "A", null));

        assertThat(r.tipo()).isEqualTo(TipoUbicacion.DEPOSITO);
    }

    @Test
    void delete_sinStockNiMovimientos_elimina() {
        Ubicacion u = Ubicacion.builder().id(1L).codigo("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(u));
        when(stockRepository.existsByUbicacionId(1L)).thenReturn(false);
        when(movimientoStockRepository.existsByUbicacionOrigenIdOrUbicacionDestinoId(1L, 1L)).thenReturn(false);

        ubicacionService.delete(1L);

        verify(ubicacionRepository).delete(u);
    }

    @Test
    void delete_conStock_lanzaBusinessException() {
        Ubicacion u = Ubicacion.builder().id(1L).codigo("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(u));
        when(stockRepository.existsByUbicacionId(1L)).thenReturn(true);

        assertThatThrownBy(() -> ubicacionService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("stock o movimientos");
    }

    @Test
    void delete_conMovimientos_lanzaBusinessException() {
        Ubicacion u = Ubicacion.builder().id(1L).codigo("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(u));
        when(stockRepository.existsByUbicacionId(1L)).thenReturn(false);
        when(movimientoStockRepository.existsByUbicacionOrigenIdOrUbicacionDestinoId(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> ubicacionService.delete(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void delete_inexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByCodigo_existente() {
        Ubicacion u = Ubicacion.builder().id(1L).codigo("A").build();
        when(ubicacionRepository.findByCodigoIgnoreCase("A")).thenReturn(Optional.of(u));

        assertThat(ubicacionService.getByCodigo("A").getCodigo()).isEqualTo("A");
    }

    @Test
    void getByCodigo_inexistente_lanza404() {
        when(ubicacionRepository.findByCodigoIgnoreCase("ZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.getByCodigo("ZZZ"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void stockResumen_agrupaPorUbicacion() {
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{1L, 10L, 3L});
        rows.add(new Object[]{2L, 5L, 2L});
        when(stockRepository.findStockAgrupadoPorUbicacion()).thenReturn(rows);

        Map<Long, StockPorUbicacionResponse> resultado = ubicacionService.stockResumen();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(1L).cantidadTotal()).isEqualTo(10L);
        assertThat(resultado.get(1L).productosDistintos()).isEqualTo(3L);
        assertThat(resultado.get(2L).cantidadTotal()).isEqualTo(5L);
    }

    @Test
    void stockDetalle_devuelveDetallePorUbicacion() {
        Ubicacion u = Ubicacion.builder().id(1L).codigo("A").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(u));
        List<Object[]> detalleRows = new java.util.ArrayList<>();
        detalleRows.add(new Object[]{10L, "SKU-1", "Filtro aceite", "Bosch", "Filtros", 5});
        when(stockRepository.findStockDetalleByUbicacionId(1L)).thenReturn(detalleRows);

        List<StockDetalleUbicacionResponse> detalle = ubicacionService.stockDetalle(1L);

        assertThat(detalle).hasSize(1);
        assertThat(detalle.get(0).productoId()).isEqualTo(10L);
        assertThat(detalle.get(0).sku()).isEqualTo("SKU-1");
        assertThat(detalle.get(0).descripcion()).isEqualTo("Filtro aceite");
        assertThat(detalle.get(0).marcaNombre()).isEqualTo("Bosch");
        assertThat(detalle.get(0).cantidad()).isEqualTo(5);
    }

    @Test
    void stockDetalle_ubicacionInexistente_lanza404() {
        when(ubicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ubicacionService.stockDetalle(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_sinTipoEnPadre_noValidaJerarquia() {
        Ubicacion padre = Ubicacion.builder().id(1L).tipo(null).codigo("X").path("X").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(padre));
        when(ubicacionRepository.save(any(Ubicacion.class))).thenAnswer(inv -> {
            Ubicacion u = inv.getArgument(0);
            u.setId(3L);
            return u;
        });

        UbicacionResponse r = ubicacionService.create(
                new UbicacionRequest(TipoUbicacion.DEPOSITO, "Y", 1L));

        assertThat(r.path()).isEqualTo("X/Y");
    }
}
