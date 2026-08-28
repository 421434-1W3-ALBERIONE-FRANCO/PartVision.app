package com.partvision.pricing.repository;

import com.partvision.pricing.domain.HistorialPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPrecioRepository extends JpaRepository<HistorialPrecio, Long> {
    List<HistorialPrecio> findByBatchId(Long batchId);
}
