package com.partvision.compras.repository;

import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByNumeroFactura(String numeroFactura);

    @EntityGraph(attributePaths = {"lineas", "lineas.producto", "lineas.producto.marca", "lineas.ubicacionIngreso"})
    Optional<Compra> findWithLineasById(Long id);

    Page<Compra> findByEstadoOrderByCreatedAtDesc(CompraEstado estado, Pageable pageable);

    Page<Compra> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
