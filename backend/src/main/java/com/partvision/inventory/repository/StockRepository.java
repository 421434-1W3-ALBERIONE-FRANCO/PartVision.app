package com.partvision.inventory.repository;

import com.partvision.inventory.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /** Bloquea la fila de stock (SELECT ... FOR UPDATE) para operar sin condiciones de carrera. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.producto.id = :productoId and s.ubicacion.id = :ubicacionId")
    Optional<Stock> lockByProductoAndUbicacion(@Param("productoId") Long productoId,
                                               @Param("ubicacionId") Long ubicacionId);

    @EntityGraph(attributePaths = {"ubicacion"})
    List<Stock> findByProductoId(Long productoId);

    boolean existsByUbicacionId(Long ubicacionId);

    /** Stock de varios productos (para enriquecer listados sin N+1). */
    @EntityGraph(attributePaths = {"ubicacion"})
    List<Stock> findByProductoIdIn(Collection<Long> productoIds);
}
