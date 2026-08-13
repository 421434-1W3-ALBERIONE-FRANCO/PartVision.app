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
 * Busqueda por palabras: cada palabra debe aparecer (case-insensitive, parcial) en la
 * descripcion, el SKU, la marca o la categoria. El orden de las palabras no importa, asi
 * "piston 1.5mm" encuentra "MOTOMEL piston trifasico ASD 1.5mm x 05mm". Al exigir todas las
 * palabras el conjunto resultante es chico, por lo que es rapido sobre catalogos grandes.
 * Trae marca y categoria con fetch join para no generar N+1 al mapear.
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
