package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Busquedas del catalogo que no encajan en los derived queries de Spring Data. */
public interface ProductoRepositoryCustom {

    /**
     * Busqueda "inteligente" por palabras, tipo candidatos: trae los productos que coinciden en
     * AL MENOS una palabra (en descripcion, SKU, marca o categoria; case-insensitive, parcial) y
     * los ordena por cuantas palabras coinciden. El orden de las palabras no importa y las que no
     * aparecen no descartan la fila, solo no suman. Asi "piston 1.5mm" o "juego de aros mahle 1.6
     * volkswagen" encuentran los productos aunque no coincidan textualmente palabra por palabra.
     */
    Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable);

    /**
     * Busqueda tolerante a errores de tipeo (fallback cuando la exacta no encuentra nada).
     * Usa la similaridad trigram de pg_trgm: cada palabra suma segun cuanto se parece a la
     * descripcion/marca/categoria (ademas del match exacto). Asi "volswagen" encuentra
     * "VOLKSWAGEN" y "mahel" encuentra "MAHLE". {@code umbral} es el minimo parecido (0..1).
     */
    Page<Producto> buscarFuzzy(List<String> tokens, Pageable pageable, double umbral);
}
