package com.partvision.location.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.repository.MovimientoStockRepository;
import com.partvision.inventory.repository.StockRepository;
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
    private final StockRepository stockRepository;
    private final MovimientoStockRepository movimientoStockRepository;

    @Transactional
    public UbicacionResponse create(UbicacionRequest request) {
        validarCodigoLibre(request.codigo(), null);
        Ubicacion parent = request.parentId() == null ? null : getEntity(request.parentId());
        validarJerarquia(request, parent);

        String path = parent == null ? request.codigo() : parent.getPath() + "/" + request.codigo();

        Ubicacion ubicacion = Ubicacion.builder()
                .parent(parent)
                .tipo(request.tipo())
                .codigo(request.codigo())
                .descripcion(request.descripcion())
                .path(path)
                .activo(true)
                .build();

        return UbicacionResponse.from(ubicacionRepository.save(ubicacion));
    }

    @Transactional
    public UbicacionResponse update(Long id, UbicacionRequest request) {
        Ubicacion ubicacion = getEntity(id);
        validarCodigoLibre(request.codigo(), id);
        ubicacion.setCodigo(request.codigo());
        ubicacion.setDescripcion(request.descripcion());
        if (request.tipo() != null) {
            ubicacion.setTipo(request.tipo());
        }
        // Modelo plano: el path acompaña al código cuando la ubicación es raíz.
        if (ubicacion.getParent() == null) {
            ubicacion.setPath(request.codigo());
        }
        return UbicacionResponse.from(ubicacionRepository.save(ubicacion));
    }

    /** Elimina una ubicación solo si está vacía (sin stock ni movimientos); si no, error de negocio. */
    @Transactional
    public void delete(Long id) {
        Ubicacion ubicacion = getEntity(id);
        boolean enUso = stockRepository.existsByUbicacionId(id)
                || movimientoStockRepository.existsByUbicacionOrigenIdOrUbicacionDestinoId(id, id);
        if (enUso) {
            throw new BusinessException(
                    "No se puede eliminar la ubicación '" + ubicacion.getCodigo()
                            + "': tiene stock o movimientos asociados.");
        }
        ubicacionRepository.delete(ubicacion);
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

    /** Resuelve una ubicación por su código (para operar por código, no por id interno). */
    @Transactional(readOnly = true)
    public Ubicacion getByCodigo(String codigo) {
        return ubicacionRepository.findByCodigoIgnoreCase(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Ubicacion (codigo)", codigo));
    }

    private void validarCodigoLibre(String codigo, Long idActual) {
        ubicacionRepository.findByCodigoIgnoreCase(codigo).ifPresent(existente -> {
            if (!existente.getId().equals(idActual)) {
                throw new DuplicateResourceException("Ya existe una ubicacion con el codigo: " + codigo);
            }
        });
    }

    private void validarJerarquia(UbicacionRequest request, Ubicacion parent) {
        if (parent != null && request.tipo() != null && parent.getTipo() != null
                && request.tipo().getProfundidad() <= parent.getTipo().getProfundidad()) {
            throw new BusinessException(
                    "Una ubicacion de tipo %s no puede colgar de una de tipo %s"
                            .formatted(request.tipo(), parent.getTipo()));
        }
    }
}
