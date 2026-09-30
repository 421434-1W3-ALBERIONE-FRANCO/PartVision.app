package com.partvision.compras.repository;

import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.domain.RevisionLinea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraLineaRepository extends JpaRepository<CompraLinea, Long> {

    /**
     * Lineas con ese codigo que todavia no tienen producto, las facturas mas nuevas primero.
     *
     * <p>Una linea con una cantidad fuera de lo normal se decide primero en Compras: si la
     * descartaron es un error de la planilla, y si nadie la miro todavia, resolverla aca podria
     * cargar esa cantidad al stock. Recien aceptada aparece entre los importados.
     */
    default Page<CompraLinea> findSinProductoPorCodigo(String codigo, Pageable pageable) {
        return findSinProductoPorCodigo(codigo, RevisionLinea.ACEPTADA, pageable);
    }

    @Query(value = "select l from CompraLinea l join fetch l.compra c "
            + "where l.codigo = :codigo and l.producto is null "
            + "and (l.revision is null or l.revision = :aceptada) "
            + "order by c.fechaFactura desc, l.id",
            countQuery = "select count(l) from CompraLinea l where l.codigo = :codigo and l.producto is null "
                    + "and (l.revision is null or l.revision = :aceptada)")
    Page<CompraLinea> findSinProductoPorCodigo(@Param("codigo") String codigo,
                                               @Param("aceptada") RevisionLinea aceptada,
                                               Pageable pageable);
}
