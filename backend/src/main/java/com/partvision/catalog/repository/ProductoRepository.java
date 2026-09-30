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

public interface ProductoRepository extends JpaRepository<Producto, Long>, ProductoRepositoryCustom {

    boolean existsByMarcaAndSku(Marca marca, String sku);

    boolean existsByMarcaAndSkuAndIdNot(Marca marca, String sku, Long id);

    boolean existsByMarca(Marca marca);

    /**
     * Unicidad de SKU en todo el catalogo, sin importar marca, proveedor ni mayusculas. La
     * del alta comun es por marca; para los SKU inventados (IMP-) tiene que ser global.
     */
    boolean existsBySkuIgnoreCase(String sku);

    /**
     * Toma el siguiente numero para un codigo IMP-. Cada llamada devuelve uno distinto aunque
     * dos altas ocurran a la vez; uno que se pide y no se usa queda salteado (ver V29).
     */
    @Query(value = "select nextval('seq_sku_importado')", nativeQuery = true)
    long siguienteNumeroImportado();

    /**
     * El numero que va a tocar en la proxima alta, sin consumirlo: solo para mostrarlo. Si otra
     * persona da de alta un importado antes, el que se asigne de verdad va a ser otro.
     */
    @Query(value = "select case when is_called then last_value + 1 else last_value end from seq_sku_importado",
            nativeQuery = true)
    long proximoNumeroImportado();

    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    Optional<Producto> findWithDetallesById(Long id);

    @EntityGraph(attributePaths = {"marca", "categoria"})
    Page<Producto> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "categoria"})
    @Query("SELECT p FROM Producto p WHERE EXISTS (SELECT 1 FROM Stock s WHERE s.producto = p AND s.cantidad > 0)")
    Page<Producto> findConStock(Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "categoria"})
    @Query("SELECT p FROM Producto p WHERE NOT EXISTS (SELECT 1 FROM Stock s WHERE s.producto = p AND s.cantidad > 0)")
    Page<Producto> findSinStock(Pageable pageable);

    // Busca por codigo de barras O por SKU: el operario encuentra el producto tipee lo
    // que tipee. left join para que tambien aparezcan productos sin codigo de barras cargado.
    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select distinct p from Producto p left join p.codigos c where c.codigo = :codigo or p.sku = :codigo")
    Optional<Producto> findByCodigo(@Param("codigo") String codigo);

    List<Producto> findBySkuIsNotNull();

    @EntityGraph(attributePaths = {"marca"})
    List<Producto> findBySkuIn(java.util.Collection<String> skus);

    // La busqueda por texto ahora es por palabras (token-AND, cualquier orden): ver
    // ProductoRepositoryCustom#buscarInteligente / ProductoRepositoryImpl.

    // Recall de candidatos para la deteccion de duplicados: productos cuyo SKU empieza con
    // el ancla (ej: "813667" trae "813667(05)" y "813667(STD)"). Trae de mas a proposito;
    // el match fino (normalizado + medida + marca) se resuelve en la capa de servicio.
    @EntityGraph(attributePaths = {"marca", "categoria", "codigos"})
    @Query("select p from Producto p where upper(p.sku) like upper(concat(:ancla, '%'))")
    List<Producto> buscarPorSkuPrefijo(@Param("ancla") String ancla, Pageable pageable);
}
