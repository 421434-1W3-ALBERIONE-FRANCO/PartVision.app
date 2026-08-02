package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    boolean existsByMarcaAndSku(Marca marca, String sku);

    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    Optional<Producto> findWithDetallesById(Long id);

    @EntityGraph(attributePaths = {"marca", "categoria"})
    Page<Producto> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select p from Producto p join p.codigos c where c.codigo = :codigo")
    Optional<Producto> findByCodigo(@Param("codigo") String codigo);
}
