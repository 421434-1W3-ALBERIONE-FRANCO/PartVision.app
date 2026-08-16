package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

/**
 * Busqueda por relevancia sobre la columna denormalizada 'busqueda' (descripcion + sku +
 * marca + categoria) con similaridad trigram de pg_trgm.
 *
 * <p><b>Filtro (rapido):</b> compara la FRASE COMPLETA contra 'busqueda' con el operador
 * {@code <%} (word_similarity, umbral 0.35). Al usar toda la frase el filtro es SELECTIVO
 * (deja pocas filas: las realmente parecidas al termino), lo que mantiene el recheck del
 * indice trigram acotado y la query rapida. Filtrar solo por la 1a palabra (ancla) matcheaba
 * decenas de miles de filas y disparaba la latencia a >1s: por eso se volvio a la frase.
 *
 * <p><b>Ranking:</b> primero un BOOST para las filas cuya 'descripcion' empieza con la 1a
 * palabra "real" del termino (tipo de pieza; salteando genericas como juego/kit/set/par), y
 * luego por parecido de la frase completa (mejor primero). Asi "cojinete 4d56 l200 2.5 8v"
 * trae los COJINETE de ese motor arriba (no juntas/valvulas que solo comparten specs), y
 * "juego de aros ..." sigue encontrando los "AROS ..." aunque no digan "juego".
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /** Parecido minimo (0..1) de la frase. 0.35 es lo mas alto que aun trae los cojinetes reales. */
    private static final String UMBRAL = "0.35";

    /** Palabras de "empaque" que no son tipo de pieza: no sirven de ancla para el boost. */
    private static final Set<String> GENERICAS = Set.of(
            "juego", "juegos", "jgo", "jgos", "kit", "kits", "set", "par", "pares");

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        // Umbral del operador <%, local a la transaccion.
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL)
                .getSingleResult();

        String frase = String.join(" ", tokens);
        String boostPrefix = likePrefix(tokens.get(anchorIndex(tokens)));

        // Solo la frase filtra (selectivo => rapido). Boost: descripcion que empieza con el tipo de pieza.
        Query data = em.createNativeQuery(
                "select p.* from productos p where :frase <% p.busqueda"
                        + " order by (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,"
                        + " word_similarity(:frase, p.busqueda) desc, char_length(p.descripcion) asc, p.id asc",
                Producto.class);
        Query count = em.createNativeQuery(
                "select count(*) from productos p where :frase <% p.busqueda");
        data.setParameter("frase", frase);
        data.setParameter("pref", boostPrefix);
        count.setParameter("frase", frase);

        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Producto> contenido = data.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(contenido, pageable, total);
    }

    /**
     * Indice de la palabra que se usa como ancla del boost (tipo de pieza): la primera
     * alfabetica de &ge;3 letras que no sea generica de empaque. Si no hay ninguna (ej. el
     * termino son puros numeros/medidas), cae a la primera palabra.
     */
    private static int anchorIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.length() >= 3 && t.chars().allMatch(Character::isLetter) && !GENERICAS.contains(t)) {
                return i;
            }
        }
        return 0;
    }

    /** Escapa los comodines de LIKE en el token y le agrega '%' para el match por prefijo. */
    private static String likePrefix(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
