package com.partvision.catalog.repository;

import com.partvision.catalog.domain.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Busquedas del catalogo que no encajan en los derived queries de Spring Data. */
public interface ProductoRepositoryCustom {

    /**
     * Busqueda "inteligente" por palabras: cada token debe aparecer (case-insensitive, parcial)
     * en la descripcion, el SKU, la marca o la categoria. El orden de las palabras no importa,
     * asi "piston 1.5mm" encuentra "MOTOMEL piston trifasico ASD 1.5mm x 05mm".
     */
    Page<Producto> buscarInteligente(List<String> tokens, Pageable pageable);
}
