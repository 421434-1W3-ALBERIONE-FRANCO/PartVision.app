package com.partvision.catalog.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.dto.CategoriaRequest;
import com.partvision.catalog.dto.CategoriaResponse;
import com.partvision.catalog.repository.CategoriaRepository;
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
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void create_categoriaRaiz() {
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CategoriaResponse response = categoriaService.create(new CategoriaRequest("Filtros", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.nombre()).isEqualTo("Filtros");
        assertThat(response.parentId()).isNull();
    }

    @Test
    void create_conPadre() {
        Categoria padre = Categoria.builder().id(1L).nombre("Filtros").build();
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(padre));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        CategoriaResponse response = categoriaService.create(new CategoriaRequest("Filtros de aceite", 1L));

        assertThat(response.parentId()).isEqualTo(1L);
        assertThat(response.parentNombre()).isEqualTo("Filtros");
    }

    @Test
    void create_padreInexistente_lanza404() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.create(new CategoriaRequest("Sub", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAll_devuelveTodas() {
        when(categoriaRepository.findAll()).thenReturn(List.of(Categoria.builder().id(1L).nombre("Filtros").build()));

        assertThat(categoriaService.findAll()).hasSize(1);
    }

    @Test
    void findById_existente() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(Categoria.builder().id(1L).nombre("Filtros").build()));

        assertThat(categoriaService.findById(1L).nombre()).isEqualTo("Filtros");
    }

    @Test
    void findById_inexistente_lanza404() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
