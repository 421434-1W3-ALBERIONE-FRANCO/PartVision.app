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
 * Busqueda por relevancia (estilo Meilisearch/OpenSearch) sobre la columna denormalizada
 * 'busqueda' (descripcion + sku + marca + categoria), con similaridad trigram de pg_trgm
 * (operador {@code <%}, tolerante a typos). En vez de comparar la frase completa como un
 * bloque (que hacia ganar subcadenas literales y dejaba que los specs de motor diluyeran la
 * palabra clave), separa el termino en dos roles:
 *
 * <ul>
 *   <li><b>Ancla</b> (la primera palabra "real": alfabetica, &ge;3 letras, salteando genericas
 *       como "juego/kit/set/par"): es el tipo de pieza y se EXIGE (filtra). Asi "cojinete ..."
 *       deja afuera juntas/balancines/valvulas que solo comparten specs del motor, y
 *       "botador ..." no se pierde entre tapas que mencionan "botador 16mm" como spec.</li>
 *   <li><b>Resto de las palabras</b> (specs: 4d56, l200, 2.5, 8v; marcas; y las genericas):
 *       NO filtran, solo RANKEAN. Cuantas mas matchean, mas arriba. Asi no se pierden filas
 *       que escriben el spec distinto ("2500CC" vs "2.5") ni las que tienen palabras de menos
 *       ("AROS ..." sigue apareciendo aunque se busque "juego de aros ...").</li>
 * </ul>
 *
 * <p>El ancla usa el indice GIN trigram sobre 'busqueda' (rapido), y el ranking se calcula solo
 * sobre las filas ya filtradas. Orden: primero un BOOST para las filas cuya 'descripcion'
 * empieza con la primera palabra tipeada, luego por cantidad de palabras que matchean, luego
 * por parecido acumulado, y a igualdad por descripcion mas corta e id.
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /** Parecido minimo (0..1) por palabra individual contra 'busqueda'. Tolera typos sin ruido. */
    private static final String UMBRAL = "0.35";

    /** Palabras de "empaque" que no son tipo de pieza: no sirven como ancla (pero si rankean). */
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

        String anchor = tokens.get(anchorIndex(tokens));

        // matched: cuantas palabras del termino matchean (ranking).  simSum: parecido acumulado.
        StringBuilder matched = new StringBuilder();
        StringBuilder simSum = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                matched.append(" + ");
                simSum.append(" + ");
            }
            matched.append("(:t").append(i).append(" <% p.busqueda)::int");
            simSum.append("word_similarity(:t").append(i).append(", p.busqueda)");
        }

        // Solo el ancla filtra; el resto rankea. Boost: descripcion que empieza con la 1a palabra.
        String sql = "select p.* from productos p where :anchor <% p.busqueda"
                + " order by (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,"
                + " (" + matched + ") desc, (" + simSum + ") desc, char_length(p.descripcion) asc, p.id asc";
        String countSql = "select count(*) from productos p where :anchor <% p.busqueda";

        Query data = em.createNativeQuery(sql, Producto.class);
        Query count = em.createNativeQuery(countSql);
        for (int i = 0; i < tokens.size(); i++) {
            data.setParameter("t" + i, tokens.get(i));
        }
        data.setParameter("anchor", anchor);
        data.setParameter("pref", likePrefix(tokens.get(0)));
        count.setParameter("anchor", anchor);

        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Producto> contenido = data.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(contenido, pageable, total);
    }

    /**
     * Indice de la palabra que se usa como ancla (tipo de pieza): la primera alfabetica de
     * &ge;3 letras que no sea generica de empaque. Si no hay ninguna (ej. el termino son puros
     * numeros/medidas), cae a la primera palabra.
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
