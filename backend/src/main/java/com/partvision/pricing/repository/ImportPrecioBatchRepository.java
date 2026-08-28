package com.partvision.pricing.repository;

import com.partvision.pricing.domain.ImportPrecioBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportPrecioBatchRepository extends JpaRepository<ImportPrecioBatch, Long> {
    List<ImportPrecioBatch> findAllByOrderByCreatedAtDesc();
}
