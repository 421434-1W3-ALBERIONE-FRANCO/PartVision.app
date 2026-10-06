package com.partvision.pricing;

import com.partvision.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

/** Las listas de precios que se mantienen solas. La primera (ADS) es la que se asume si no se dice cual. */
@Component
class FuentesListas {

    private final List<FuenteLista> fuentes;

    FuentesListas(AdsSyncProperties ads, EgsaRecepcionProperties egsa) {
        this.fuentes = List.of(FuenteLista.de(ads), FuenteLista.de(egsa));
    }

    List<FuenteLista> todas() {
        return fuentes;
    }

    /** @param proveedor nombre del proveedor ("Autopartes del Sur") o el corto ("ADS"), sin importar mayusculas; vacio = ADS */
    FuenteLista de(String proveedor) {
        if (proveedor == null || proveedor.isBlank()) return fuentes.getFirst();
        return fuentes.stream()
                .filter(f -> f.proveedor().equalsIgnoreCase(proveedor.trim()) || f.nombre().equalsIgnoreCase(proveedor.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No hay actualización automática para el proveedor " + proveedor));
    }
}
