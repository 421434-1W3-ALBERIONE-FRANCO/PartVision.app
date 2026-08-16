package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Busquedas del catalogo que no encajan en los derived queries de Spring Data. */
public interface ProductoRepositoryCustom {

    /**
     * Busqueda por relevancia (trigram, tolerante a typos) sobre la columna denormalizada
     * 'busqueda' (descripcion + SKU + marca + categoria). Filtra por la FRASE COMPLETA con el
     * operador {@code <%} (selectivo => rapido) y rankea con un BOOST para las filas cuya
     * descripcion empieza con la primera palabra "real" (tipo de pieza), y luego por parecido
     * de la frase. Asi "cojinete 4d56 l200 2.5 8v" prioriza los COJINETE de ese motor por
     * encima de juntas/valvulas que solo comparten specs, "botador 16" prioriza los BOTADOR
     * reales, y "juego de aros ..." igual encuentra los "AROS ..." aunque no digan "juego".
     */
    Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable);
}
