package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Implementacion de la busqueda por palabras, tipo "candidatos". Cada palabra que aparece
 * (LIKE %token%, en minuscula) en descripcion, SKU, marca o categoria suma un punto; se traen
 * las filas que coinciden en AL MENOS una palabra y se ordenan por cuantas coinciden (mejor
 * arriba). Asi "juego de aros mahle 1.6 volkswagen" encuentra "MAHLE ... VOLKSWAGEN 1.6 ..."
 * aunque no diga "juego": esa palabra simplemente no suma, pero las otras rankean la fila.
 * Trae marca y categoria con fetch join (sin N+1). El indice trigram sobre descripcion acelera.
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        // Predicado por palabra: coincide en descripcion, SKU, marca o categoria.
        StringBuilder where = new StringBuilder();      // OR de todas las palabras (al menos una)
        StringBuilder score = new StringBuilder();      // suma de palabras que coinciden (ranking)
        for (int i = 0; i < tokens.size(); i++) {
            String pred = "(lower(p.descripcion) like :t" + i
                    + " or lower(p.sku) like :t" + i
                    + " or lower(m.nombre) like :t" + i
                    + " or lower(c.nombre) like :t" + i + ")";
            where.append(i == 0 ? "" : " or ").append(pred);
            score.append(i == 0 ? "" : " + ").append("case when ").append(pred).append(" then 1 else 0 end");
        }

        TypedQuery<Producto> data = em.createQuery(
                "select p from Producto p left join fetch p.marca m left join fetch p.categoria c"
                        + " where " + where
                        + " order by (" + score + ") desc, length(p.descripcion) asc, p.id asc",
                Producto.class);
        TypedQuery<Long> count = em.createQuery(
                "select count(p) from Producto p left join p.marca m left join p.categoria c"
                        + " where " + where,
                Long.class);

        for (int i = 0; i < tokens.size(); i++) {
            String patron = "%" + tokens.get(i).toLowerCase() + "%";
            data.setParameter("t" + i, patron);
            count.setParameter("t" + i, patron);
        }

        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());

        List<Producto> contenido = data.getResultList();
        return new PageImpl<>(contenido, pageable, count.getSingleResult());
    }
}
