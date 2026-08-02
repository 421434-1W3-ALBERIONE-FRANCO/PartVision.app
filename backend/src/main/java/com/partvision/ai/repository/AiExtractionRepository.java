package com.partvision.ai.repository;

import com.partvision.ai.domain.AiExtraction;
import com.partvision.ai.domain.EstadoExtraccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiExtractionRepository extends JpaRepository<AiExtraction, Long> {

    Page<AiExtraction> findByEstadoOrderByCreatedAtDesc(EstadoExtraccion estado, Pageable pageable);
}
