package com.partvision.location.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.dto.UbicacionRequest;
import com.partvision.location.dto.UbicacionResponse;
import com.partvision.location.repository.UbicacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UbicacionService {

    private final UbicacionRepository ubicacionRepository;

    @Transactional
    public UbicacionResponse create(UbicacionRequest request) {
        Ubicacion parent = request.parentId() == null ? null : getEntity(request.parentId());
        validarJerarquia(request, parent);

        String path = parent == null ? request.codigo() : parent.getPath() + "/" + request.codigo();

        Ubicacion ubicacion = Ubicacion.builder()
                .parent(parent)
                .tipo(request.tipo())
                .codigo(request.codigo())
                .path(path)
                .activo(true)
                .build();

        return UbicacionResponse.from(ubicacionRepository.save(ubicacion));
    }

    @Transactional(readOnly = true)
    public UbicacionResponse findById(Long id) {
        return UbicacionResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<UbicacionResponse> findRaices() {
        return ubicacionRepository.findByParentIsNullOrderByCodigo().stream()
                .map(UbicacionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UbicacionResponse> findHijos(Long parentId) {
        getEntity(parentId); // valida que el padre exista -> 404 si no
        return ubicacionRepository.findByParentIdOrderByCodigo(parentId).stream()
                .map(UbicacionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Ubicacion getEntity(Long id) {
        return ubicacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ubicacion", id));
    }

    private void validarJerarquia(UbicacionRequest request, Ubicacion parent) {
        if (parent != null && request.tipo().getProfundidad() <= parent.getTipo().getProfundidad()) {
            throw new BusinessException(
                    "Una ubicacion de tipo %s no puede colgar de una de tipo %s"
                            .formatted(request.tipo(), parent.getTipo()));
        }
    }
}
