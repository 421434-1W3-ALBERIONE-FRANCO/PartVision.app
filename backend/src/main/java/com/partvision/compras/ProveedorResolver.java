package com.partvision.compras;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduce el proveedor que viene en la factura al nombre con que esta en el catalogo. Importa
 * porque hay ~10.300 SKU cargados una vez por proveedor, y el nombre es lo unico que decide a
 * cual de los dos productos va el stock: "ADS" tiene que encontrar a "Autopartes del Sur".
 *
 * <p>Los alias se configuran como {@code CLAVE=Nombre del catalogo;OTRA=Otro nombre} en
 * {@code partvision.compras.proveedor-alias}. La comparacion ignora mayusculas, tildes y
 * espacios de mas.
 */
@Component
public class ProveedorResolver {

    private final Map<String, String> alias;

    public ProveedorResolver(
            @Value("${partvision.compras.proveedor-alias:ADS=Autopartes del Sur}") String configuracion) {
        Map<String, String> leidos = new HashMap<>();
        for (String par : configuracion.split(";")) {
            int igual = par.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            String clave = par.substring(0, igual);
            String nombre = par.substring(igual + 1).trim();
            if (clave.isBlank() || nombre.isEmpty()) {
                continue;
            }
            leidos.put(LectorSheet.normalizar(clave), nombre);
        }
        this.alias = Map.copyOf(leidos);
    }

    /** Nombre del catalogo para el proveedor de la factura; sin alias, el texto tal cual. */
    public String resolver(String proveedor) {
        if (proveedor == null || proveedor.isBlank()) {
            return null;
        }
        return alias.getOrDefault(LectorSheet.normalizar(proveedor), proveedor.trim());
    }
}
