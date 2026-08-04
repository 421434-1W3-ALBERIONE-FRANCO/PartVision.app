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

    // Busca por codigo de barras O por SKU: el operario encuentra el producto tipee lo
    // que tipee. left join para que tambien aparezcan productos sin codigo de barras cargado.
    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select distinct p from Producto p left join p.codigos c where c.codigo = :codigo or p.sku = :codigo")
    Optional<Producto> findByCodigo(@Param("codigo") String codigo);
}
