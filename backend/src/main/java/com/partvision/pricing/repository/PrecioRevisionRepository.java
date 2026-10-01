package com.partvision.pricing.repository;

import com.partvision.pricing.domain.EstadoRevisionPrecio;
import com.partvision.pricing.domain.PrecioRevision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PrecioRevisionRepository extends JpaRepository<PrecioRevision, Long> {

    @Query("select r from PrecioRevision r join fetch r.producto p "
            + "where r.estado = :estado and lower(p.proveedor) = lower(:proveedor) "
            + "order by abs(r.variacionPct) desc, r.id")
    List<PrecioRevision> findPendientes(@Param("estado") EstadoRevisionPrecio estado,
                                        @Param("proveedor") String proveedor, Pageable pageable);

    @Query("select count(r) from PrecioRevision r "
            + "where r.estado = :estado and lower(r.producto.proveedor) = lower(:proveedor)")
    long contarPorEstado(@Param("estado") EstadoRevisionPrecio estado, @Param("proveedor") String proveedor);

    @Query("select r from PrecioRevision r join fetch r.producto where r.id in :ids")
    List<PrecioRevision> findConProductoByIdIn(@Param("ids") Collection<Long> ids);

    /** Una corrida nueva reemplaza lo que habia quedado pendiente de las anteriores. */
    @Modifying
    @Query("update PrecioRevision r set r.estado = :vencida, r.resueltaEn = :ahora "
            + "where r.estado = :pendiente and r.producto.id in "
            + "(select p.id from Producto p where lower(p.proveedor) = lower(:proveedor))")
    int vencerPendientes(@Param("pendiente") EstadoRevisionPrecio pendiente,
                         @Param("vencida") EstadoRevisionPrecio vencida,
                         @Param("proveedor") String proveedor,
                         @Param("ahora") LocalDateTime ahora);
}
