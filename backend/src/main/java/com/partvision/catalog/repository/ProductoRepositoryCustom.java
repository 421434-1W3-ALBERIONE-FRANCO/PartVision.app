package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Busquedas del catalogo que no encajan en los derived queries de Spring Data. */
public interface ProductoRepositoryCustom {

    /**
     * Busqueda por relevancia (trigram, tolerante a typos) sobre la columna denormalizada
     * 'busqueda' (descripcion + SKU + marca + categoria). La primera palabra "real" (tipo de
     * pieza) es el ANCLA y se exige (filtra); el resto de las palabras no descartan la fila,
     * solo suben su ranking cuando matchean. Rankea primero las filas cuya descripcion empieza
     * con la primera palabra tipeada, luego por cantidad de palabras que matchean. Asi
     * "cojinete 4d56 l200 2.5 8v" prioriza los COJINETE reales por encima de juntas/balancines
     * que solo comparten specs del motor, "botador 16" prioriza los BOTADOR reales sobre las
     * tapas que mencionan "botador 16mm", y "juego de aros ..." igual encuentra los "AROS ..."
     * aunque no digan "juego".
     */
    Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable);
}
