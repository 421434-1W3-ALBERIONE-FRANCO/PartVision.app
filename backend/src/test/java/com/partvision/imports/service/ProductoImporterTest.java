package com.partvision.imports.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.repository.CategoriaRepository;
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

    @Mock private CategoriaRepository categoriaRepository;
    @Mock private ProductoService productoService;
    @InjectMocks private ProductoImporter importer;

    private final ArgumentCaptor<ProductoRequest> captor = ArgumentCaptor.forClass(ProductoRequest.class);

    @Test
    void importar_pasaMarcaPorNombreYCreaCategoriaNuevaYElCodigo() {
        when(categoriaRepository.findByNombreIgnoreCaseAndParentIsNull("Filtros")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(Categoria.class)))
                .thenReturn(Categoria.builder().id(7L).nombre("Filtros").build());

        importer.importar(new ProductoImporter.FilaProducto(
                "ABC-123", "Bosch", "Filtros", "Filtro de aceite", "779123", "EAN13", "EGSA"));

        verify(productoService).create(captor.capture());
        ProductoRequest req = captor.getValue();
        // La marca viaja como texto: el catalogo la resuelve o la crea.
        assertThat(req.marcaId()).isNull();
        assertThat(req.marcaNombre()).isEqualTo("Bosch");
        assertThat(req.categoriaId()).isEqualTo(7L);
        assertThat(req.descripcion()).isEqualTo("Filtro de aceite");
        assertThat(req.codigos()).hasSize(1);
        assertThat(req.codigos().get(0).codigo()).isEqualTo("779123");
        assertThat(req.proveedor()).isEqualTo("EGSA");
    }

    @Test
    void importar_reutilizaCategoriaExistente() {
        when(categoriaRepository.findByNombreIgnoreCaseAndParentIsNull("Filtros"))
                .thenReturn(Optional.of(Categoria.builder().id(7L).nombre("Filtros").build()));

        importer.importar(new ProductoImporter.FilaProducto(
                null, "Bosch", "Filtros", "Filtro", null, null, null));

        verify(categoriaRepository, never()).save(any());
        verify(productoService).create(captor.capture());
        assertThat(captor.getValue().marcaNombre()).isEqualTo("Bosch");
    }

    @Test
    void importar_sinMarcaCategoriaNiCodigo() {
        importer.importar(new ProductoImporter.FilaProducto(
                null, null, null, "Repuesto suelto", null, null, null));

        verify(categoriaRepository, never()).findByNombreIgnoreCaseAndParentIsNull(any());
        verify(productoService).create(captor.capture());
        ProductoRequest req = captor.getValue();
        assertThat(req.marcaId()).isNull();
        assertThat(req.marcaNombre()).isNull();
        assertThat(req.categoriaId()).isNull();
        assertThat(req.codigos()).isEmpty();
    }
}
