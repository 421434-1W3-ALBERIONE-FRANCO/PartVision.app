package com.partvision.catalog.repository;

import com.partvision.catalog.domain.ProductoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoCodigoRepository extends JpaRepository<ProductoCodigo, Long> {

    boolean existsByCodigo(String codigo);
}
