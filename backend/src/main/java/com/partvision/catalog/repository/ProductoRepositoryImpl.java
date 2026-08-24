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
 * Busqueda estilo Meilisearch sobre la columna denormalizada 'busqueda', en tres niveles:
 *
 * <ol>
 *   <li><b>FTS prefix AND.</b> Todos los tokens con prefix match ({@code :*}), combinados
 *       con AND. Maxima precision: solo devuelve productos que contienen TODOS los terminos.
 *       Soporta busqueda as-you-type ("pist" encuentra "pistones").</li>
 *   <li><b>FTS anchor + rest OR.</b> El ancla (tipo de pieza) es obligatorio, el resto es
 *       opcional. Mas recall cuando algun token no matchea exacto.</li>
 *   <li><b>Trigram fallback.</b> Similaridad difusa por frase completa ({@code <%}),
 *       tolerante a errores de tipeo.</li>
 * </ol>
 */
public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    private static final String FTS = "simple";
    private static final String UMBRAL_TRIGRAM = "0.3";

    private static final Set<String> GENERICAS = Set.of(
            "juego", "juegos", "jgo", "jgos", "kit", "kits", "set", "par", "pares");

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable) {
        return buscarInteligenteInterno(tokens, null, pageable);
    }

    @Override
    public Page<Producto> buscarInteligenteConStock(List<String> tokens, boolean tieneStock, Pageable pageable) {
        return buscarInteligenteInterno(tokens, tieneStock, pageable);
    }

    private Page<Producto> buscarInteligenteInterno(List<String> tokens, Boolean tieneStock, Pageable pageable) {
        if (tokens.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        int anchorIdx = anchorIndex(tokens);

        List<String> prefixed = new ArrayList<>();
        for (String t : tokens) {
            String s = ftsSanitize(t);
            if (!s.isEmpty()) {
                prefixed.add(s + ":*");
            }
        }

        if (!prefixed.isEmpty()) {
            // Level 1: all tokens AND with prefix (most precise, as-you-type)
            if (prefixed.size() >= 2) {
                String tsqAll = String.join(" & ", prefixed);
                Page<Producto> r = ejecutarFts(tsqAll, tokens, anchorIdx, tieneStock, pageable);
                if (r.getTotalElements() > 0) {
                    return r;
                }
            }

            // Level 2: anchor AND rest OR with prefix (broader recall)
            String anchorSanitized = ftsSanitize(tokens.get(anchorIdx));
            if (!anchorSanitized.isEmpty()) {
                List<String> rest = new ArrayList<>();
                for (int i = 0; i < tokens.size(); i++) {
                    if (i == anchorIdx) continue;
                    String s = ftsSanitize(tokens.get(i));
                    if (!s.isEmpty()) rest.add(s + ":*");
                }
                String tsq = rest.isEmpty()
                        ? anchorSanitized + ":*"
                        : anchorSanitized + ":* & (" + String.join(" | ", rest) + ")";
                Page<Producto> r = ejecutarFts(tsq, tokens, anchorIdx, tieneStock, pageable);
                if (r.getTotalElements() > 0) {
                    return r;
                }
            }
        }

        // Level 3: trigram fallback
        return buscarTrigram(tokens, anchorIdx, tieneStock, pageable);
    }

    private Page<Producto> ejecutarFts(String tsquery, List<String> tokens, int anchorIdx,
                                       Boolean tieneStock, Pageable pageable) {
        String stockClause = stockFilter(tieneStock);

        StringBuilder order = new StringBuilder(" order by");
        if (tokens.size() >= 2) {
            order.append(" (case when");
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) order.append(" and");
                order.append(" lower(p.descripcion) like :dc").append(i).append(" escape '\\'");
            }
            order.append(" then 0 else 1 end) asc,");
        }
        order.append(" (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,");
        order.append(" ts_rank(to_tsvector('").append(FTS).append("', p.busqueda), to_tsquery('")
                .append(FTS).append("', :tsq)) desc,");
        order.append(" char_length(p.descripcion) asc, p.id asc");

        String sql = "select p.* from productos p"
                + " where to_tsvector('" + FTS + "', p.busqueda) @@ to_tsquery('" + FTS + "', :tsq)"
                + stockClause + order;
        String countSql = "select count(*) from productos p"
                + " where to_tsvector('" + FTS + "', p.busqueda) @@ to_tsquery('" + FTS + "', :tsq)"
                + stockClause;

        Query data = em.createNativeQuery(sql, Producto.class);
        Query count = em.createNativeQuery(countSql);
        data.setParameter("tsq", tsquery);
        if (tokens.size() >= 2) {
            for (int i = 0; i < tokens.size(); i++) {
                data.setParameter("dc" + i, "%" + likeEscape(tokens.get(i)) + "%");
            }
        }
        data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
        count.setParameter("tsq", tsquery);

        return paginar(data, count, pageable);
    }

    private Page<Producto> buscarTrigram(List<String> tokens, int anchorIdx, Boolean tieneStock, Pageable pageable) {
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL_TRIGRAM)
                .getSingleResult();

        String stockClause = stockFilter(tieneStock);
        String frase = String.join(" ", tokens);

        StringBuilder order = new StringBuilder(" order by");
        if (tokens.size() >= 2) {
            order.append(" (case when");
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) order.append(" and");
                order.append(" lower(p.descripcion) like :dc").append(i).append(" escape '\\'");
            }
            order.append(" then 0 else 1 end) asc,");
        }
        order.append(" (case when lower(p.descripcion) like :pref escape '\\' then 0 else 1 end) asc,");
        order.append(" word_similarity(:frase, p.busqueda) desc, char_length(p.descripcion) asc, p.id asc");

        Query data = em.createNativeQuery(
                "select p.* from productos p where :frase <% p.busqueda" + stockClause + order,
                Producto.class);
        Query count = em.createNativeQuery(
                "select count(*) from productos p where :frase <% p.busqueda" + stockClause);
        data.setParameter("frase", frase);
        data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
        if (tokens.size() >= 2) {
            for (int i = 0; i < tokens.size(); i++) {
                data.setParameter("dc" + i, "%" + likeEscape(tokens.get(i)) + "%");
            }
        }
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

    private static int anchorIndex(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.length() >= 3 && t.chars().allMatch(Character::isLetter) && !GENERICAS.contains(t)) {
                return i;
            }
        }
        return 0;
    }

    private static String ftsSanitize(String token) {
        String limpio = token.replaceAll("[^a-z0-9.]", "");
        return limpio.chars().anyMatch(Character::isLetterOrDigit) ? limpio : "";
    }

    private static String likeEscape(String token) {
        return token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String likePrefix(String token) {
        return likeEscape(token) + "%";
    }

    private static String stockFilter(Boolean tieneStock) {
        if (tieneStock == null) {
            return "";
        }
        String op = tieneStock ? "EXISTS" : "NOT EXISTS";
        return " AND " + op + " (SELECT 1 FROM stock s WHERE s.producto_id = p.id AND s.cantidad > 0)";
    }
}
