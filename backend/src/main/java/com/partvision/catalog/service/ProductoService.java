package com.partvision.catalog.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.domain.ProductoCodigo;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoCodigoRequest;
import com.partvision.catalog.dto.ProductoListItemResponse;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.repository.ProductoCodigoRepository;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoCodigoRepository productoCodigoRepository;
    private final MarcaService marcaService;
    private final CategoriaService categoriaService;

    @Transactional
    public ProductoResponse create(ProductoRequest request) {
        Marca marca = request.marcaId() == null ? null : marcaService.getEntity(request.marcaId());
        Categoria categoria = request.categoriaId() == null ? null : categoriaService.getEntity(request.categoriaId());

        validarSkuUnico(request, marca);
        List<ProductoCodigoRequest> codigos = request.codigos() == null ? List.of() : request.codigos();
        validarCodigosUnicos(codigos);

        Producto producto = Producto.builder()
                .sku(request.sku())
                .marca(marca)
                .categoria(categoria)
                .descripcion(request.descripcion())
                .proveedor(request.proveedor())
                .estado(request.estado() == null ? ProductoEstado.ACTIVO : request.estado())
                .detallesExtra(request.detallesExtra() == null ? new HashMap<>() : new HashMap<>(request.detallesExtra()))
                .build();

        codigos.forEach(c -> producto.addCodigo(
                ProductoCodigo.builder().codigo(c.codigo()).tipo(c.tipo()).build()));

        return ProductoResponse.from(productoRepository.save(producto));
    }

    @Transactional(readOnly = true)
    public ProductoResponse findById(Long id) {
        return ProductoResponse.from(getEntity(id));
    }

    /** Uso interno de otros modulos (ej: inventario). */
    @Transactional(readOnly = true)
    public Producto getEntity(Long id) {
        return productoRepository.findWithDetallesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto", id));
    }

    @Transactional(readOnly = true)
    public Page<ProductoListItemResponse> findAll(Pageable pageable) {
        return productoRepository.findAllBy(pageable).map(ProductoListItemResponse::from);
    }

    /** Busqueda por texto parcial (descripcion, SKU, marca, categoria). Paginada. */
    @Transactional(readOnly = true)
    public Page<ProductoListItemResponse> buscarPorTexto(String q, Pageable pageable) {
        return productoRepository.buscarPorTexto(q, pageable).map(ProductoListItemResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductoResponse buscarPorCodigo(String codigo) {
        Producto producto = productoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un producto con el codigo: " + codigo));
        return ProductoResponse.from(producto);
    }

    private void validarSkuUnico(ProductoRequest request, Marca marca) {
        if (request.sku() != null && marca != null
                && productoRepository.existsByMarcaAndSku(marca, request.sku())) {
            throw new DuplicateResourceException(
                    "Ya existe un producto con SKU '" + request.sku() + "' para esa marca");
        }
    }

    private void validarCodigosUnicos(List<ProductoCodigoRequest> codigos) {
        for (ProductoCodigoRequest codigo : codigos) {
            if (productoCodigoRepository.existsByCodigo(codigo.codigo())) {
                throw new DuplicateResourceException("El codigo ya esta registrado: " + codigo.codigo());
            }
        }
    }
}
