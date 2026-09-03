package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProductoRepositoryImpl implements ProductoRepositoryCustom {

    private static final String FTS = "simple";
    private static final String UMBRAL_TRIGRAM = "0.3";

    private static final Set<String> GENERICAS = Set.of(
            "juego", "juegos", "jgo", "jgos", "kit", "kits", "set", "par", "pares");

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "sku", "p.sku",
            "descripcion", "p.descripcion",
            "estado", "p.estado",
            "marca.nombre", "m.nombre",
            "categoria.nombre", "c.nombre",
            "id", "p.id");

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Producto> buscarInteligente(List<String> tokens, String rawQuery, Pageable pageable) {
        return buscarInteligenteInterno(tokens, rawQuery, null, pageable);
    }

    @Override
    public Page<Producto> buscarInteligenteConStock(List<String> tokens, String rawQuery, boolean tieneStock, Pageable pageable) {
        return buscarInteligenteInterno(tokens, rawQuery, tieneStock, pageable);
    }

    private Page<Producto> buscarInteligenteInterno(List<String> tokens, String rawQuery, Boolean tieneStock, Pageable pageable) {
        if (tokens.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        int anchorIdx = anchorIndex(tokens);
        boolean userSort = hasUserSort(pageable);

        List<String> prefixed = new ArrayList<>();
        for (String t : tokens) {
            String s = ftsSanitize(t);
            if (!s.isEmpty()) {
                prefixed.add(s + ":*");
            }
        }

        if (!prefixed.isEmpty()) {
            if (prefixed.size() >= 2) {
                String tsqAll = String.join(" & ", prefixed);
                Page<Producto> r = ejecutarFts(tsqAll, tokens, anchorIdx, rawQuery, tieneStock, pageable, userSort);
                if (r.getTotalElements() > 0) {
                    return r;
                }
            }

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
                Page<Producto> r = ejecutarFts(tsq, tokens, anchorIdx, rawQuery, tieneStock, pageable, userSort);
                if (r.getTotalElements() > 0) {
                    return r;
                }
            }
        }

        return buscarTrigram(tokens, anchorIdx, rawQuery, tieneStock, pageable, userSort);
    }

    private Page<Producto> ejecutarFts(String tsquery, List<String> tokens, int anchorIdx,
                                       String rawQuery, Boolean tieneStock, Pageable pageable, boolean userSort) {
        String stockClause = stockFilter(tieneStock);
        String sortJoins = userSort ? buildSortJoins(pageable) : "";
        String order = userSort
                ? buildUserOrderBy(pageable)
                : buildRelevanceOrderFts(tokens, anchorIdx);

        String ftsWhere = " where to_tsvector('" + FTS + "', p.busqueda) @@ to_tsquery('" + FTS + "', :tsq)";

        String sql = "select p.* from productos p" + sortJoins + ftsWhere + stockClause + order;
        String countSql = "select count(*) from productos p" + ftsWhere + stockClause;

        Query data = em.createNativeQuery(sql, Producto.class);
        Query count = em.createNativeQuery(countSql);
        data.setParameter("tsq", tsquery);
        count.setParameter("tsq", tsquery);

        if (!userSort) {
            data.setParameter("skuExact", rawQuery.trim().toLowerCase());
            data.setParameter("skuPref", likeEscape(rawQuery.trim().toLowerCase()) + "%");
            if (tokens.size() >= 2) {
                for (int i = 0; i < tokens.size(); i++) {
                    data.setParameter("dc" + i, "%" + likeEscape(tokens.get(i)) + "%");
                }
            }
            data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
        }

        return paginar(data, count, pageable);
    }

    private Page<Producto> buscarTrigram(List<String> tokens, int anchorIdx, String rawQuery,
                                         Boolean tieneStock, Pageable pageable, boolean userSort) {
        em.createNativeQuery("SELECT set_config('pg_trgm.word_similarity_threshold', :u, true)")
                .setParameter("u", UMBRAL_TRIGRAM)
                .getSingleResult();

        String stockClause = stockFilter(tieneStock);
        String frase = String.join(" ", tokens);
        String sortJoins = userSort ? buildSortJoins(pageable) : "";
        String order = userSort
                ? buildUserOrderBy(pageable)
                : buildRelevanceOrderTrigram(tokens, anchorIdx);

        String trigramWhere = " where :frase <% p.busqueda";

        Query data = em.createNativeQuery(
                "select p.* from productos p" + sortJoins + trigramWhere + stockClause + order,
                Producto.class);
        Query count = em.createNativeQuery(
                "select count(*) from productos p" + trigramWhere + stockClause);

        data.setParameter("frase", frase);
        count.setParameter("frase", frase);

        if (!userSort) {
            data.setParameter("skuExact", rawQuery.trim().toLowerCase());
            data.setParameter("skuPref", likeEscape(rawQuery.trim().toLowerCase()) + "%");
            data.setParameter("pref", likePrefix(tokens.get(anchorIdx)));
            if (tokens.size() >= 2) {
                for (int i = 0; i < tokens.size(); i++) {
                    data.setParameter("dc" + i, "%" + likeEscape(tokens.get(i)) + "%");
                }
            }
        }

        return paginar(data, count, pageable);
    }

    // ── Sort helpers ──────────────────────────────────────────────────────

    private static boolean hasUserSort(Pageable pageable) {
        return pageable.getSort().isSorted();
    }

    private static String buildSortJoins(Pageable pageable) {
        boolean needsMarca = false, needsCategoria = false;
        for (Sort.Order o : pageable.getSort()) {
            if (o.getProperty().startsWith("marca.")) needsMarca = true;
            if (o.getProperty().startsWith("categoria.")) needsCategoria = true;
        }
        StringBuilder sb = new StringBuilder();
        if (needsMarca) sb.append(" left join marcas m on p.marca_id = m.id");
        if (needsCategoria) sb.append(" left join categorias c on p.categoria_id = c.id");
        return sb.toString();
    }

    private static String buildUserOrderBy(Pageable pageable) {
        StringBuilder order = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order o : pageable.getSort()) {
            String col = SORT_COLUMNS.get(o.getProperty());
            if (col == null) continue;
            if (!first) order.append(", ");
            first = false;
            order.append(col);
            order.append(o.isAscending() ? " asc" : " desc");
            order.append(" nulls last");
        }
        if (first) return " order by p.id asc";
        order.append(", p.id asc");
        return order.toString();
    }

    // ── Relevance ORDER BY (used when no user sort) ───────────────────────

    private static String buildRelevanceOrderFts(List<String> tokens, int anchorIdx) {
        StringBuilder order = new StringBuilder(" order by");
        order.append(" (case when lower(p.sku) = :skuExact then 0")
                .append(" when lower(p.sku) like :skuPref escape '\\' then 1 else 2 end) asc,");
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
        return order.toString();
    }

    private static String buildRelevanceOrderTrigram(List<String> tokens, int anchorIdx) {
        StringBuilder order = new StringBuilder(" order by");
        order.append(" (case when lower(p.sku) = :skuExact then 0")
                .append(" when lower(p.sku) like :skuPref escape '\\' then 1 else 2 end) asc,");
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
        return order.toString();
    }

    // ── Utilities ─────────────────────────────────────────────────────────

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
