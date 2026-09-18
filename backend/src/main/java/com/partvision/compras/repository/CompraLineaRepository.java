package com.partvision.compras.repository;

import com.partvision.compras.domain.CompraLinea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraLineaRepository extends JpaRepository<CompraLinea, Long> {

    /** Lineas con ese codigo que todavia no tienen producto, las facturas mas nuevas primero. */
    @Query(value = "select l from CompraLinea l join fetch l.compra c "
            + "where l.codigo = :codigo and l.producto is null "
            + "order by c.fechaFactura desc, l.id",
            countQuery = "select count(l) from CompraLinea l where l.codigo = :codigo and l.producto is null")
    Page<CompraLinea> findSinProductoPorCodigo(@Param("codigo") String codigo, Pageable pageable);
}
