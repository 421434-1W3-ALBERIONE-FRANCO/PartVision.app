package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Busqueda por relevancia sobre la columna denormalizada 'busqueda' (descripcion + sku +
 * marca + categoria), en dos capas:
 *
 * <ol>
 *   <li><b>Full-Text Search (primaria, rapida).</b> Recupera candidatos con el indice invertido
 *       {@code to_tsvector('simple', busqueda)} (config 'simple' para no romper codigos/medidas
 *       ni nombres de modelo). Para no traer decenas de miles de filas de una palabra comun, la
 *       tsquery exige el ANCLA (tipo de pieza) y deja el resto en OR:
 *       {@code ancla & (t1 | t2 | ...)}. Asi el set de candidatos queda acotado (2-5k) y el
 *       ranking es barato. Ranking: BOOST para descripcion que empieza con el ancla, luego
 *       {@code ts_rank} (cuando hay varias palabras), luego descripcion mas corta e id.</li>
 *   <li><b>Trigram (fallback).</b> Si FTS no encuentra nada (typo "pistpn", o una forma que el
 *       indice de palabras no reconoce), cae a la busqueda difusa por frase completa con
 *       {@code <%} (word_similarity), tolerante a errores de tipeo.</li>
 * </ol>
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    /** Config de FTS. 'simple' = sin stemming: preserva 1.6/8v/ea111 y modelos (suran, no "sur"). */
    private static final String FTS = "simple";

    /** Parecido minimo (0..1) de la frase para el fallback trigram. */
    private static final String UMBRAL_TRIGRAM = "0.35";

    /** Palabras de "empaque" que no son tipo de pieza: no sirven de ancla. */
    private static final Set<String> GENERICAS = Set.of(
            "juego", "juegos", "jgo", "jgos", "kit", "kits", "set", "par", "pares");

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        int anchorIdx = anchorIndex(tokens);
        String anchorFts = ftsSanitize(tokens.get(anchorIdx));
        if (!anchorFts.isEmpty()) {
            Page<Producto> fts = buscarFts(tokens, anchorIdx, anchorFts, pageable);
            if (fts.getTotalElements() > 0) {
                return fts;
            }
        }
        // Fallback difuso (typos / formas no reconocidas por el indice de palabras).
        return buscarTrigram(tokens, anchorIdx, pageable);
    }

    /** Recuperacion primaria por FTS: {@code ancla & (resto en OR)}. */
    private Page<Producto> buscarFts(List<String> tokens, int anchorIdx, String anchorFts, Pageable pageable) {
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (i == anchorIdx) {
                continue;
            }
            String s = ftsSanitize(tokens.get(i));
            if (!s.isEmpty()) {
                rest.add(s);
            }
        }
        String tsquery = rest.isEmpty()
                ? anchorFts
                : anchorFts + " & (" + String.join(" | ", rest) + ")";

        // Con varias palabras el set es chico => ts_rank es barato y ordena bien.
        // Con una sola palabra el set puede ser grande => se omite ts_rank (seria caro y no aporta).
        String rankOrder = rest.isEmpty()
                ? ""
                : " ts_rank(to_tsvector('" + FTS + "', p.busqueda), to_tsquery('" + FTS + "', :tsq)) desc,";

        String sql = "select p.* from productos p"
                + " where to_tsvector('" + FTS + "', p.busqueda) @@ to_tsquery('" + FTS + "', :tsq)"
                + " order by (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,"
                + rankOrder
                + " char_length(p.descripcion) asc, p.id asc";
        String countSql = "select count(*) from productos p"
                + " where to_tsvector('" + FTS + "', p.busqueda) @@ to_tsquery('" + FTS + "', :tsq)";

        Query data = em.createNativeQuery(sql, Producto.class);
        Query count = em.createNativeQuery(countSql);
        data.setParameter("tsq", tsquery);
        data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
        count.setParameter("tsq", tsquery);

        return paginar(data, count, pageable);
    }

    /** Fallback difuso: frase completa con similaridad trigram (tolera typos). */
    private Page<Producto> buscarTrigram(List<String> tokens, int anchorIdx, Pageable pageable) {
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL_TRIGRAM)
                .getSingleResult();

        String frase = String.join(" ", tokens);
        Query data = em.createNativeQuery(
                "select p.* from productos p where :frase <% p.busqueda"
                        + " order by (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,"
                        + " word_similarity(:frase, p.busqueda) desc, char_length(p.descripcion) asc, p.id asc",
                Producto.class);
        Query count = em.createNativeQuery(
                "select count(*) from productos p where :frase <% p.busqueda");
        data.setParameter("frase", frase);
        data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
        count.setParameter("frase", frase);

        return paginar(data, count, pageable);
    }

    private Page<Producto> paginar(Query data, Query count, Pageable pageable) {
        data.setFirstResult((int) pageable.getOffset());
        data.setMaxResults(pageable.getPageSize());
        @SuppressWarnings("unchecked")
        List<Producto> contenido = data.getResultList();
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(contenido, pageable, total);
    }

    /**
     * Indice de la palabra que se usa como ancla (tipo de pieza): la primera alfabetica de
     * &ge;3 letras que no sea generica de empaque. Si no hay ninguna, cae a la primera palabra.
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

    /**
     * Deja el token apto para una tsquery: solo letras/digitos/punto (ya viene en minuscula),
     * sin operadores de tsquery. Si no queda ningun caracter alfanumerico, devuelve "".
     */
    private static String ftsSanitize(String token) {
        String limpio = token.replaceAll("[^a-z0-9.]", "");
        return limpio.chars().anyMatch(Character::isLetterOrDigit) ? limpio : "";
    }

    /** Escapa los comodines de LIKE en el token y le agrega '%' para el match por prefijo. */
    private static String likePrefix(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
