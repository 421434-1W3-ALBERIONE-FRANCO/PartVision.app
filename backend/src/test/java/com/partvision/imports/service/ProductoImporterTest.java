package com.partvision.imports.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.repository.CategoriaRepository;
import com.partvision.catalog.repository.MarcaRepository;
import com.partvision.catalog.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoImporterTest {

    @Mock private MarcaRepository marcaRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProductoService productoService;
    @InjectMocks private ProductoImporter importer;

    private final ArgumentCaptor<ProductoRequest> captor = ArgumentCaptor.forClass(ProductoRequest.class);

    @Test
    void importar_creaMarcaYCategoriaNuevasYElCodigo() {
        when(marcaRepository.findByNombreIgnoreCase("Bosch")).thenReturn(Optional.empty());
        when(marcaRepository.save(any(Marca.class))).thenReturn(Marca.builder().id(5L).nombre("Bosch").build());
        when(categoriaRepository.findByNombreIgnoreCaseAndParentIsNull("Filtros")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(Categoria.class)))
                .thenReturn(Categoria.builder().id(7L).nombre("Filtros").build());

        importer.importar(new ProductoImporter.FilaProducto(
                "ABC-123", "Bosch", "Filtros", "Filtro de aceite", "779123", "EAN13"));

        verify(productoService).create(captor.capture());
        ProductoRequest req = captor.getValue();
        assertThat(req.marcaId()).isEqualTo(5L);
        assertThat(req.categoriaId()).isEqualTo(7L);
        assertThat(req.descripcion()).isEqualTo("Filtro de aceite");
        assertThat(req.codigos()).hasSize(1);
        assertThat(req.codigos().get(0).codigo()).isEqualTo("779123");
    }

    @Test
    void importar_reutilizaMarcaYCategoriaExistentes() {
        when(marcaRepository.findByNombreIgnoreCase("Bosch"))
                .thenReturn(Optional.of(Marca.builder().id(5L).nombre("Bosch").build()));
        when(categoriaRepository.findByNombreIgnoreCaseAndParentIsNull("Filtros"))
                .thenReturn(Optional.of(Categoria.builder().id(7L).nombre("Filtros").build()));

        importer.importar(new ProductoImporter.FilaProducto(
                null, "Bosch", "Filtros", "Filtro", null, null));

        verify(marcaRepository, never()).save(any());
        verify(categoriaRepository, never()).save(any());
        verify(productoService).create(captor.capture());
        assertThat(captor.getValue().marcaId()).isEqualTo(5L);
    }

    @Test
    void importar_sinMarcaCategoriaNiCodigo() {
        importer.importar(new ProductoImporter.FilaProducto(
                null, null, null, "Repuesto suelto", null, null));

        verify(marcaRepository, never()).findByNombreIgnoreCase(any());
        verify(categoriaRepository, never()).findByNombreIgnoreCaseAndParentIsNull(any());
        verify(productoService).create(captor.capture());
        ProductoRequest req = captor.getValue();
        assertThat(req.marcaId()).isNull();
        assertThat(req.categoriaId()).isNull();
        assertThat(req.codigos()).isEmpty();
    }
}
