package com.partvision.location.repository;

import com.partvision.location.domain.Ubicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    List<Ubicacion> findByParentIsNullOrderByCodigo();

    List<Ubicacion> findByParentIdOrderByCodigo(Long parentId);

    /** Resolución por código (el identificador del cliente), sin distinguir mayúsculas. */
    Optional<Ubicacion> findByCodigoIgnoreCase(String codigo);
}
