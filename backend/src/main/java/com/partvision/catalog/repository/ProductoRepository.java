package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    boolean existsByMarcaAndSku(Marca marca, String sku);

    boolean existsByMarcaAndSkuAndIdNot(Marca marca, String sku, Long id);

    boolean existsByMarca(Marca marca);

    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    Optional<Producto> findWithDetallesById(Long id);

    @EntityGraph(attributePaths = {"marca", "categoria"})
    Page<Producto> findAllBy(Pageable pageable);

    // Busca por codigo de barras O por SKU: el operario encuentra el producto tipee lo
    // que tipee. left join para que tambien aparezcan productos sin codigo de barras cargado.
    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select distinct p from Producto p left join p.codigos c where c.codigo = :codigo or p.sku = :codigo")
    Optional<Producto> findByCodigo(@Param("codigo") String codigo);

    // Busqueda por texto parcial (case-insensitive) sobre descripcion, SKU, marca y
    // categoria: el operario tipea "bujia", "volkswagen" o el codigo y filtra por lista.
    @EntityGraph(attributePaths = {"marca", "categoria"})
    @Query("""
            select p from Producto p
            left join p.marca m
            left join p.categoria c
            where lower(p.descripcion) like lower(concat('%', :q, '%'))
               or lower(p.sku) like lower(concat('%', :q, '%'))
               or lower(m.nombre) like lower(concat('%', :q, '%'))
               or lower(c.nombre) like lower(concat('%', :q, '%'))
            """)
    Page<Producto> buscarPorTexto(@Param("q") String q, Pageable pageable);

    // Recall de candidatos para la deteccion de duplicados: productos cuyo SKU empieza con
    // el ancla (ej: "813667" trae "813667(05)" y "813667(STD)"). Trae de mas a proposito;
    // el match fino (normalizado + medida + marca) se resuelve en la capa de servicio.
    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select p from Producto p where upper(p.sku) like upper(concat(:ancla, '%'))")
    List<Producto> buscarPorSkuPrefijo(@Param("ancla") String ancla, Pageable pageable);
}
