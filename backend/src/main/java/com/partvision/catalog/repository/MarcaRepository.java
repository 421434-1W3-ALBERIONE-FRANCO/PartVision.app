package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaRepository extends JpaRepository<Marca, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
}
