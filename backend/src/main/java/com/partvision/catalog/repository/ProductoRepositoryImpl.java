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
 * Busqueda "candidatos" rapida y tolerante. Compara TODO el termino (frase) contra la columna
 * denormalizada 'busqueda' (descripcion + sku + marca + categoria) con la similaridad trigram
 * de pg_trgm (operador {@code <%}). Al ser por parecido de la frase completa:
 *  - no importa el orden de las palabras,
 *  - tolera errores de tipeo ("pistpn fiat" -> PISTON FIAT, "volswagen" -> VOLKSWAGEN),
 *  - tolera palabras de mas o de menos ("juego de aros ..." rankea igual los aros),
 * y usa el indice GIN trigram sobre 'busqueda', asi que es rapido (~200ms) sobre 135k.
 * Se ordena por parecido (mejor primero).
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /** Parecido minimo (0..1) de la frase. 0.45 tolera typos y palabras de mas con poco ruido. */
    private static final String UMBRAL = "0.45";

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        // Umbral del operador <%, local a la transaccion.
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL)
                .getSingleResult();

        String frase = String.join(" ", tokens);

        Query data = em.createNativeQuery(
                "select p.* from productos p where :q <% p.busqueda"
                        + " order by word_similarity(:q, p.busqueda) desc, char_length(p.descripcion) asc, p.id asc",
                Producto.class);
        Query count = em.createNativeQuery(
                "select count(*) from productos p where :q <% p.busqueda");
        data.setParameter("q", frase);
        count.setParameter("q", frase);

        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Producto> contenido = data.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(contenido, pageable, total);
    }
}
