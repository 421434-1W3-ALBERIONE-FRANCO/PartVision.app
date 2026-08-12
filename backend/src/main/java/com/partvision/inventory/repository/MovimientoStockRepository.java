package com.partvision.inventory.repository;

import com.partvision.inventory.domain.MovimientoStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    Page<MovimientoStock> findByProductoIdOrderByCreatedAtDesc(Long productoId, Pageable pageable);

    boolean existsByUbicacionOrigenIdOrUbicacionDestinoId(Long ubicacionOrigenId, Long ubicacionDestinoId);
}
