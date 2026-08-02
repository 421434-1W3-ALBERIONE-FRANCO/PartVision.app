package com.partvision.location.repository;

import com.partvision.location.domain.Ubicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

    List<Ubicacion> findByParentIsNullOrderByCodigo();

    List<Ubicacion> findByParentIdOrderByCodigo(Long parentId);
}
