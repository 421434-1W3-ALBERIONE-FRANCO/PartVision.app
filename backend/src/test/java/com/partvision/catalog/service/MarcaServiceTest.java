package com.partvision.catalog.service;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.dto.MarcaRequest;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.repository.MarcaRepository;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcaServiceTest {

    @Mock
    private MarcaRepository marcaRepository;
    @Mock
    private ProductoRepository productoRepository;
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
    void update_renombraMarca() {
        Marca existente = Marca.builder().id(1L).nombre("Bosh").build();
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(marcaRepository.findByNombreIgnoreCase("Bosch")).thenReturn(Optional.empty());
        when(marcaRepository.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        MarcaResponse response = marcaService.update(1L, new MarcaRequest("Bosch"));

        assertThat(response.nombre()).isEqualTo("Bosch");
    }

    @Test
    void update_mismoNombre_noChoca() {
        Marca existente = Marca.builder().id(1L).nombre("Bosch").build();
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(marcaRepository.findByNombreIgnoreCase("Bosch")).thenReturn(Optional.of(existente));
        when(marcaRepository.save(any(Marca.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(marcaService.update(1L, new MarcaRequest("Bosch")).nombre()).isEqualTo("Bosch");
    }

    @Test
    void update_nombreDeOtraMarca_lanza409() {
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(Marca.builder().id(1L).nombre("Bosch").build()));
        when(marcaRepository.findByNombreIgnoreCase("Fram"))
                .thenReturn(Optional.of(Marca.builder().id(2L).nombre("Fram").build()));

        assertThatThrownBy(() -> marcaService.update(1L, new MarcaRequest("Fram")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_inexistente_lanza404() {
        when(marcaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.update(99L, new MarcaRequest("Bosch")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_sinProductos_elimina() {
        Marca marca = Marca.builder().id(1L).nombre("Bosch").build();
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(marca));
        when(productoRepository.existsByMarca(marca)).thenReturn(false);

        marcaService.delete(1L);

        verify(marcaRepository).delete(marca);
    }

    @Test
    void delete_conProductos_lanza422() {
        Marca marca = Marca.builder().id(1L).nombre("Bosch").build();
        when(marcaRepository.findById(1L)).thenReturn(Optional.of(marca));
        when(productoRepository.existsByMarca(marca)).thenReturn(true);

        assertThatThrownBy(() -> marcaService.delete(1L)).isInstanceOf(BusinessException.class);
        verify(marcaRepository, never()).delete(any());
    }

    @Test
    void getOrCreateByNombre_existente_reutiliza() {
        Marca marca = Marca.builder().id(1L).nombre("Bosch").build();
        when(marcaRepository.findByNombreIgnoreCase("Bosch")).thenReturn(Optional.of(marca));

        assertThat(marcaService.getOrCreateByNombre("  Bosch  ").getId()).isEqualTo(1L);
        verify(marcaRepository, never()).save(any());
    }

    @Test
    void getOrCreateByNombre_inexistente_crea() {
        when(marcaRepository.findByNombreIgnoreCase("Fram")).thenReturn(Optional.empty());
        when(marcaRepository.save(any(Marca.class))).thenAnswer(inv -> {
            Marca m = inv.getArgument(0);
            m.setId(9L);
            return m;
        });

        Marca creada = marcaService.getOrCreateByNombre("Fram");

        assertThat(creada.getId()).isEqualTo(9L);
        assertThat(creada.getNombre()).isEqualTo("Fram");
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
