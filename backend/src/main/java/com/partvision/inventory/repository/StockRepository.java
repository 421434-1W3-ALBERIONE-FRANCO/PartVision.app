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

    @Query("SELECT s.ubicacion.id, COALESCE(SUM(s.cantidad), 0), COUNT(DISTINCT s.producto.id) " +
            "FROM Stock s WHERE s.cantidad > 0 GROUP BY s.ubicacion.id")
    List<Object[]> findStockAgrupadoPorUbicacion();

    @Query("SELECT s.producto.id, s.producto.sku, s.producto.descripcion, " +
            "m.nombre, c.nombre, s.cantidad " +
            "FROM Stock s LEFT JOIN s.producto.marca m LEFT JOIN s.producto.categoria c " +
            "WHERE s.ubicacion.id = :ubicacionId AND s.cantidad > 0 " +
            "ORDER BY s.cantidad DESC")
    List<Object[]> findStockDetalleByUbicacionId(@Param("ubicacionId") Long ubicacionId);

    @Query("SELECT DISTINCT s.ubicacion.id FROM Stock s " +
            "LEFT JOIN s.producto.marca m " +
            "LEFT JOIN s.producto.codigos pc " +
            "WHERE s.cantidad > 0 AND (" +
            "LOWER(s.producto.descripcion) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.producto.sku) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(m.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(pc.codigo) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Long> findUbicacionIdsByProductoTexto(@Param("q") String q);
}
