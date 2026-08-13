package com.partvision.imports.service;

import com.partvision.catalog.domain.Categoria;
import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.repository.CategoriaRepository;
import com.partvision.catalog.repository.MarcaRepository;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.imports.dto.ImportResultResponse.FilaError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Camino RAPIDO de importacion masiva (para el import asincrono de catalogos grandes).
 *
 * Elimina el N+1 del camino fila-por-fila: en vez de ~5 consultas por fila (lookup/creacion
 * de marca y categoria, chequeo de SKU y de codigo, insert), precarga marcas/categorias y los
 * sets de deduplicacion en memoria (1 consulta cada uno) e inserta los productos en LOTES por
 * JDBC. Baja la carga de ~20min a bajo el minuto.
 *
 * Mantiene la misma semantica de dedup que el camino lento:
 *  - por codigo (unico global) y por (marca, sku) cuando ambos estan presentes.
 * Las filas con codigo (que crean filas en producto_codigos) se derivan al camino lento por
 * fila ({@link ProductoImporter}); los catalogos de proveedor no traen codigo, asi que el 100%
 * de esas filas va por el lote. Si un lote falla por un dato malo, se reintenta fila-por-fila
 * para no perder el lote entero (importacion parcial).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoBulkImporter {

    /** Filas por lote JDBC. Con 8 parametros por fila queda muy por debajo del limite de Postgres. */
    private static final int LOTE = 500;

    private static final String INSERT_SQL = """
            INSERT INTO productos
                (sku, marca_id, categoria_id, descripcion, estado, proveedor, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Tipos SQL de los parametros del INSERT. Explicitarlos es clave para las filas con marca_id
     * o categoria_id NULL (p. ej. Autopartes del Sur): sin esto Postgres no infiere el tipo del
     * parametro nulo y el lote falla.
     */
    private static final int[] INSERT_TIPOS = {
            Types.VARCHAR,   // sku
            Types.BIGINT,    // marca_id
            Types.BIGINT,    // categoria_id
            Types.VARCHAR,   // descripcion
            Types.VARCHAR,   // estado
            Types.VARCHAR,   // proveedor
            Types.TIMESTAMP, // created_at
            Types.TIMESTAMP  // updated_at
    };

    private final JdbcTemplate jdbcTemplate;
    private final MarcaRepository marcaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoImporter productoImporter; // fallback lento por fila

    /**
     * Inserta todas las filas (ya deduplicadas dentro del archivo) reportando el progreso
     * en {@code job}. Cada fila termina contada como importada u omitida (o error).
     */
    public void importar(List<ProductoImporter.FilaProducto> filas, ImportJob job) {
        if (filas.isEmpty()) {
            return;
        }
        Map<String, Long> marcas = resolverMarcas(filas);
        Map<String, Long> categorias = resolverCategorias(filas);
        Set<String> skusExistentes = cargarSkusExistentes();
        Set<String> codigosExistentes = cargarCodigosExistentes();
        Timestamp ahora = Timestamp.from(Instant.now());

        List<Object[]> lote = new ArrayList<>(LOTE);
        List<ProductoImporter.FilaProducto> loteFilas = new ArrayList<>(LOTE);

        for (ProductoImporter.FilaProducto fila : filas) {
            Long marcaId = fila.marca() == null ? null : marcas.get(fila.marca().toLowerCase());
            Long categoriaId = fila.categoria() == null ? null : categorias.get(fila.categoria().toLowerCase());

            boolean dupSku = marcaId != null && fila.sku() != null
                    && skusExistentes.contains(claveSku(marcaId, fila.sku()));
            boolean dupCodigo = fila.codigo() != null
                    && codigosExistentes.contains(fila.codigo().toLowerCase());
            if (dupSku || dupCodigo) {
                job.marcarOmitida();
                job.marcarProcesada();
                continue;
            }

            // Las filas con codigo crean producto_codigos: van por el camino lento (raro en catalogos).
            if (fila.codigo() != null) {
                importarFilaLenta(fila, job);
                codigosExistentes.add(fila.codigo().toLowerCase());
                continue;
            }

            lote.add(new Object[]{
                    fila.sku(), marcaId, categoriaId, fila.descripcion(), "ACTIVO", fila.proveedor(), ahora, ahora});
            loteFilas.add(fila);
            if (marcaId != null && fila.sku() != null) {
                skusExistentes.add(claveSku(marcaId, fila.sku()));
            }
            if (lote.size() >= LOTE) {
                insertarLote(lote, loteFilas, job);
            }
        }
        if (!lote.isEmpty()) {
            insertarLote(lote, loteFilas, job);
        }
    }

    /** Inserta el lote por JDBC. Si falla (dato invalido), reintenta fila-por-fila via JPA. */
    private void insertarLote(List<Object[]> lote, List<ProductoImporter.FilaProducto> loteFilas, ImportJob job) {
        try {
            jdbcTemplate.batchUpdate(INSERT_SQL, lote, INSERT_TIPOS);
            for (int i = 0; i < lote.size(); i++) {
                job.marcarImportada();
                job.marcarProcesada();
            }
        } catch (DataAccessException ex) {
            log.warn("Lote de {} filas fallo, reintentando fila-por-fila: {}", lote.size(), ex.getMessage());
            for (ProductoImporter.FilaProducto fila : loteFilas) {
                importarFilaLenta(fila, job);
            }
        } finally {
            lote.clear();
            loteFilas.clear();
        }
    }

    /** Camino lento (JPA) para una fila: usado en fallback y para filas con codigo. */
    private void importarFilaLenta(ProductoImporter.FilaProducto fila, ImportJob job) {
        try {
            productoImporter.importar(fila);
            job.marcarImportada();
        } catch (DuplicateResourceException ex) {
            job.marcarOmitida();
        } catch (Exception ex) {
            job.agregarError(new FilaError(0, ex.getMessage()));
        } finally {
            job.marcarProcesada();
        }
    }

    /** Precarga las marcas existentes (1 consulta) y crea las que falten. Devuelve nombreLower -> id. */
    private Map<String, Long> resolverMarcas(List<ProductoImporter.FilaProducto> filas) {
        Map<String, Long> map = new HashMap<>();
        marcaRepository.findAll().forEach(m -> map.putIfAbsent(m.getNombre().toLowerCase(), m.getId()));
        for (ProductoImporter.FilaProducto fila : filas) {
            String nombre = fila.marca();
            if (nombre != null && !map.containsKey(nombre.toLowerCase())) {
                Marca marca = marcaRepository.save(Marca.builder().nombre(nombre).build());
                map.put(nombre.toLowerCase(), marca.getId());
            }
        }
        return map;
    }

    /** Precarga las categorias raiz existentes (1 consulta) y crea las que falten. nombreLower -> id. */
    private Map<String, Long> resolverCategorias(List<ProductoImporter.FilaProducto> filas) {
        Map<String, Long> map = new HashMap<>();
        categoriaRepository.findAll().forEach(c -> {
            if (c.getParent() == null) {
                map.putIfAbsent(c.getNombre().toLowerCase(), c.getId());
            }
        });
        for (ProductoImporter.FilaProducto fila : filas) {
            String nombre = fila.categoria();
            if (nombre != null && !map.containsKey(nombre.toLowerCase())) {
                Categoria categoria = categoriaRepository.save(Categoria.builder().nombre(nombre).build());
                map.put(nombre.toLowerCase(), categoria.getId());
            }
        }
        return map;
    }

    /** Set de (marca_id|sku) ya existentes en la BD, para dedup por SKU sin consultar por fila. */
    private Set<String> cargarSkusExistentes() {
        Set<String> set = new HashSet<>();
        jdbcTemplate.query(
                "SELECT marca_id, sku FROM productos WHERE sku IS NOT NULL AND marca_id IS NOT NULL",
                (RowCallbackHandler) rs -> set.add(claveSku(rs.getLong("marca_id"), rs.getString("sku"))));
        return set;
    }

    /** Set de codigos ya registrados, para dedup por codigo sin consultar por fila. */
    private Set<String> cargarCodigosExistentes() {
        Set<String> set = new HashSet<>();
        jdbcTemplate.query("SELECT codigo FROM producto_codigos",
                (RowCallbackHandler) rs -> set.add(rs.getString("codigo").toLowerCase()));
        return set;
    }

    private String claveSku(long marcaId, String sku) {
        return marcaId + "|" + sku.toLowerCase();
    }
}
