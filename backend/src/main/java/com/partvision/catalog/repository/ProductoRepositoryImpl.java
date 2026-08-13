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
 * Implementacion de la busqueda por palabras. Arma dinamicamente la condicion segun cuantos
 * tokens haya: por cada palabra exige que aparezca (LIKE %token%, en minuscula) en descripcion,
 * SKU, marca o categoria. Trae marca y categoria con fetch join para no generar N+1 al mapear.
 * El indice trigram sobre descripcion (gin_trgm_ops) acelera los LIKE '%...%'.
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        for (int i = 0; i < tokens.size(); i++) {
            where.append(" and (lower(p.descripcion) like :t").append(i)
                    .append(" or lower(p.sku) like :t").append(i)
                    .append(" or lower(m.nombre) like :t").append(i)
                    .append(" or lower(c.nombre) like :t").append(i)
                    .append(')');
        }

        TypedQuery<Producto> data = em.createQuery(
                "select p from Producto p left join fetch p.marca m left join fetch p.categoria c"
                        + where + " order by length(p.descripcion) asc, p.id asc",
                Producto.class);
        TypedQuery<Long> count = em.createQuery(
                "select count(p) from Producto p left join p.marca m left join p.categoria c" + where,
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
