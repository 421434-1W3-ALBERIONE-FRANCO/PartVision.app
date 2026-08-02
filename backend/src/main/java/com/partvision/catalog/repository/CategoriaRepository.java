package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByNombreIgnoreCaseAndParentIsNull(String nombre);
}
