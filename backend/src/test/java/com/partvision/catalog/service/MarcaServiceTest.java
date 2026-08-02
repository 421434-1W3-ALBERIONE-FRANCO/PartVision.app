package com.partvision.catalog.service;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.dto.MarcaRequest;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.repository.MarcaRepository;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcaServiceTest {

    @Mock
    private MarcaRepository marcaRepository;
    @InjectMocks
    private MarcaService marcaService;

    @Test
    void create_guardaMarcaNueva() {
        when(marcaRepository.existsByNombreIgnoreCase("Bosch")).thenReturn(false);
        when(marcaRepository.save(any(Marca.class))).thenAnswer(inv -> {
            Marca m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        MarcaResponse response = marcaService.create(new MarcaRequest("Bosch"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nombre()).isEqualTo("Bosch");
    }

    @Test
    void create_marcaDuplicada_lanza409() {
        when(marcaRepository.existsByNombreIgnoreCase("Bosch")).thenReturn(true);

        assertThatThrownBy(() -> marcaService.create(new MarcaRequest("Bosch")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void findAll_devuelveTodas() {
        when(marcaRepository.findAll()).thenReturn(List.of(
                Marca.builder().id(1L).nombre("Bosch").build(),
                Marca.builder().id(2L).nombre("Fram").build()));

        assertThat(marcaService.findAll()).extracting(MarcaResponse::nombre)
                .containsExactly("Bosch", "Fram");
    }

    @Test
    void findById_existente_devuelveMarca() {
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(Marca.builder().id(1L).nombre("Bosch").build()));

        assertThat(marcaService.findById(1L).nombre()).isEqualTo("Bosch");
    }

    @Test
    void findById_inexistente_lanza404() {
        when(marcaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
