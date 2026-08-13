package com.partvision.imports.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantenimiento del catalogo. Pensado para reiniciar una carga masiva que quedo con
 * duplicados y volver a importar desde cero.
 */
@Service
@RequiredArgsConstructor
public class CatalogoMantenimientoService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Vacia el catalogo de productos y sus datos dependientes (codigos, stock y movimientos),
     * PRESERVANDO ubicaciones, marcas, categorias y usuarios. Borra en orden de dependencia
     * para no violar las claves foraneas. Devuelve cuantos productos se eliminaron.
     */
    @Transactional
    public int vaciarCatalogo() {
        jdbcTemplate.update("DELETE FROM movimientos_stock");
        jdbcTemplate.update("DELETE FROM stock");
        jdbcTemplate.update("DELETE FROM producto_codigos");
        // Las extracciones de IA se conservan como historial; solo se desvincula el producto.
        jdbcTemplate.update("UPDATE ai_extractions SET producto_id = NULL WHERE producto_id IS NOT NULL");
        return jdbcTemplate.update("DELETE FROM productos");
    }
}
