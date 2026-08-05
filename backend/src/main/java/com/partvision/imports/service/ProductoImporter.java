package com.partvision.imports.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.dto.ProductoCodigoRequest;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.repository.CategoriaRepository;
import com.partvision.catalog.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Importa una fila de producto en su propia transaccion, de modo que el fallo
 * de una fila no afecte a las demas (importacion parcial).
 *
 * Decision: marca y categoria se resuelven por nombre y se crean si no existen
 * (los catalogos suelen venir en la misma planilla). Esto acelera la carga a
 * costa de posibles duplicados por tipeo, que se normalizan luego.
 */
@Service
@RequiredArgsConstructor
public class ProductoImporter {

    private final CategoriaRepository categoriaRepository;
    private final ProductoService productoService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importar(FilaProducto fila) {
        // La marca se resuelve/crea por nombre dentro del catalogo (ProductoRequest.marcaNombre).
        Long categoriaId = fila.categoria() == null ? null : resolverOCrearCategoria(fila.categoria()).getId();

        List<ProductoCodigoRequest> codigos = fila.codigo() == null
                ? List.of()
                : List.of(new ProductoCodigoRequest(fila.codigo(), fila.tipoCodigo()));

        productoService.create(new ProductoRequest(
                fila.sku(), null, fila.marca(), categoriaId, fila.descripcion(), null, null, codigos, fila.proveedor()));
    }

    private Categoria resolverOCrearCategoria(String nombre) {
        return categoriaRepository.findByNombreIgnoreCaseAndParentIsNull(nombre)
                .orElseGet(() -> categoriaRepository.save(Categoria.builder().nombre(nombre).build()));
    }

    /** Datos de una fila del archivo, ya normalizados (blancos -> null). */
    public record FilaProducto(
            String sku,
            String marca,
            String categoria,
            String descripcion,
            String codigo,
            String tipoCodigo,
            String proveedor
    ) {
    }
}
