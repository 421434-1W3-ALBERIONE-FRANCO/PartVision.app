package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Busqueda por palabras, rapida y tolerante a errores de tipeo. Cada palabra debe parecerse
 * (similaridad trigram, operador {@code <%}) a la columna denormalizada 'busqueda' (descripcion
 * + sku + marca + categoria). El orden de las palabras no importa y se toleran typos:
 * "aros mahek volswagen" encuentra "MAHLE ... VOLKSWAGEN". El operador usa el indice GIN trigram
 * sobre 'busqueda', asi que es rapido (~40ms) sobre catalogos grandes. Se ordena por parecido.
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /** Parecido minimo (0..1) por palabra. 0.4 tolera typos (mahel~MAHLE=0.5) con poco ruido. */
    private static final String UMBRAL = "0.4";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        // Umbral de similaridad, local a la transaccion (afecta al operador <%).
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL)
                .getSingleResult();

        StringBuilder where = new StringBuilder();
        StringBuilder score = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            where.append(i == 0 ? "" : " and ").append(":t").append(i).append(" <% p.busqueda");
            score.append(i == 0 ? "" : " + ").append("word_similarity(:t").append(i).append(", p.busqueda)");
        }

        String sql = "select p.* from productos p where " + where
                + " order by (" + score + ") desc, char_length(p.descripcion) asc, p.id asc";
        String countSql = "select count(*) from productos p where " + where;

        Query data = em.createNativeQuery(sql, Producto.class);
        Query count = em.createNativeQuery(countSql);
        for (int i = 0; i < tokens.size(); i++) {
            data.setParameter("t" + i, tokens.get(i));
            count.setParameter("t" + i, tokens.get(i));
        }

        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Producto> contenido = data.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(contenido, pageable, total);
    }
}
