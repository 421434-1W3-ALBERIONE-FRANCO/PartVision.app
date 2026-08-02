package com.partvision.catalog.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.dto.CategoriaRequest;
import com.partvision.catalog.dto.CategoriaResponse;
import com.partvision.catalog.repository.CategoriaRepository;
import com.partvision.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional
    public CategoriaResponse create(CategoriaRequest request) {
        Categoria parent = request.parentId() == null ? null : getEntity(request.parentId());
        // La unicidad (nombre, parent) la garantiza la restriccion de la BD.
        Categoria categoria = categoriaRepository.save(
                Categoria.builder().nombre(request.nombre()).parent(parent).build());
        return CategoriaResponse.from(categoria);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> findAll() {
        return categoriaRepository.findAll().stream().map(CategoriaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse findById(Long id) {
        return CategoriaResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Categoria getEntity(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria", id));
    }
}
