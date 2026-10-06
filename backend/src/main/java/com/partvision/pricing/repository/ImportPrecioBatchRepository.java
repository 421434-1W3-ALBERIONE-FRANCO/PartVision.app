package com.partvision.pricing.repository;

import com.partvision.pricing.domain.ImportPrecioBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportPrecioBatchRepository extends JpaRepository<ImportPrecioBatch, Long> {
    List<ImportPrecioBatch> findAllByOrderByCreatedAtDesc();

    /** La ultima lista cargada a mano (o por el robot, por la pantalla) que sigue aplicada. */
    Optional<ImportPrecioBatch> findFirstByProveedorIgnoreCaseAndFuenteAndEstadoOrderByCreatedAtDesc(
            String proveedor, String fuente, String estado);
}
