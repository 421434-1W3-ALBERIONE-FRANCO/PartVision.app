package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Busquedas del catalogo que no encajan en los derived queries de Spring Data. */
public interface ProductoRepositoryCustom {

    /**
     * Busqueda por relevancia en dos capas sobre la columna denormalizada 'busqueda'
     * (descripcion + SKU + marca + categoria): Full-Text Search como recuperacion primaria
     * (indice invertido, milisegundos; tsquery {@code ancla & (resto en OR)} para acotar
     * candidatos), con ranking por boost de tipo de pieza + {@code ts_rank}; y fallback a
     * similaridad trigram cuando FTS no encuentra nada (typos). Asi "cojinete 4d56 l200 2.5 8v"
     * prioriza los COJINETE de ese motor, "juego de aros ..." encuentra los "AROS ...", y
     * "pistpn" (con typo) igual cae en PISTON via el fallback.
     */
    Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable);
}
