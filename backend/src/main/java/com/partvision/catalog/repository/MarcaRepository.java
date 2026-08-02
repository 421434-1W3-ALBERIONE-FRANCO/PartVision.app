package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarcaRepository extends JpaRepository<Marca, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Marca> findByNombreIgnoreCase(String nombre);
}
