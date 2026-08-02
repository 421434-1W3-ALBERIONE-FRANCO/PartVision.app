package com.partvision.catalog.service;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.dto.MarcaRequest;
import com.partvision.catalog.dto.MarcaResponse;
import com.partvision.catalog.repository.MarcaRepository;
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

    @Transactional
    public MarcaResponse create(MarcaRequest request) {
        if (marcaRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new DuplicateResourceException("La marca ya existe: " + request.nombre());
        }
        Marca marca = marcaRepository.save(Marca.builder().nombre(request.nombre()).build());
        return MarcaResponse.from(marca);
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
}
