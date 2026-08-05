package com.partvision.catalog.service;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.dto.MarcaRequest;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.repository.MarcaRepository;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarcaService {

    private final MarcaRepository marcaRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public MarcaResponse create(MarcaRequest request) {
        if (marcaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new DuplicateResourceException("La marca ya existe: " + request.nombre());
        }
        Marca marca = marcaRepository.save(Marca.builder().nombre(request.nombre()).build());
        return MarcaResponse.from(marca);
    }

    @Transactional
    public MarcaResponse update(Long id, MarcaRequest request) {
        Marca marca = getEntity(id);
        // Solo choca si el nombre nuevo pertenece a OTRA marca (renombrar a si misma es valido).
        marcaRepository.findByNombreIgnoreCase(request.nombre())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new DuplicateResourceException("La marca ya existe: " + request.nombre());
                });
        marca.setNombre(request.nombre());
        return MarcaResponse.from(marcaRepository.save(marca));
    }

    @Transactional
    public void delete(Long id) {
        Marca marca = getEntity(id);
        if (productoRepository.existsByMarca(marca)) {
            throw new BusinessException(
                    "No se puede eliminar la marca porque tiene productos asociados: " + marca.getNombre());
        }
        marcaRepository.delete(marca);
    }

    @Transactional(readOnly = true)
    public List<MarcaResponse> findAll() {
        return marcaRepository.findAll().stream().map(MarcaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MarcaResponse findById(Long id) {
        return MarcaResponse.from(getEntity(id));
    }

    /** Uso interno del modulo catalogo (ej: ProductoService). */
    @Transactional(readOnly = true)
    public Marca getEntity(Long id) {
        return marcaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marca", id));
    }

    /**
     * Resuelve una marca por nombre; la crea si no existe. Uso interno del catalogo:
     * permite que el alta por IA (o carga rapida) persista la marca detectada como texto
     * sin exigir que el cliente resuelva el id primero.
     */
    @Transactional
    public Marca getOrCreateByNombre(String nombre) {
        String limpio = nombre.trim();
        return marcaRepository.findByNombreIgnoreCase(limpio)
                .orElseGet(() -> marcaRepository.save(Marca.builder().nombre(limpio).build()));
    }
}
