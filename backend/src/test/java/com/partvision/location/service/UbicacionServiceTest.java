package com.partvision.location.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.location.domain.TipoUbicacion;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.dto.UbicacionRequest;
import com.partvision.location.dto.UbicacionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.partvision.location.repository.UbicacionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UbicacionServiceTest {

    @Mock
    private UbicacionRepository ubicacionRepository;
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
        Ubicacion nivel = Ubicacion.builder().id(1L).tipo(TipoUbicacion.NIVEL).codigo("2").path("A/1/4/2").build();
        when(ubicacionRepository.findById(1L)).thenReturn(Optional.of(nivel));

        assertThatThrownBy(() -> ubicacionService.create(new UbicacionRequest(TipoUbicacion.DEPOSITO, "B", 1L)))
                .isInstanceOf(BusinessException.class);
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
}
