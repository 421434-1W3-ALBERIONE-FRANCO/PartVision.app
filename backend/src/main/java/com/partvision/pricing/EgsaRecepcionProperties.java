package com.partvision.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Recepcion de la lista de precios de EGSA. EGSA no tiene portal ni API: es un programa de
 * escritorio en la PC del cliente, asi que el que empuja el archivo es el robot de ese equipo
 * ({@code POST /api/v1/precios/recepcion/egsa}), protegido con esta clave.
 *
 * <p>Queda apagada mientras no este {@code EGSA_API_KEY} en el entorno del servidor (falla
 * cerrada, como la recepcion de compras). Mientras tanto el robot sigue pudiendo cargar la lista
 * por la pantalla de Precios.
 *
 * <p>Los umbrales son los mismos frenos que ADS: una lista que llega rara no se aplica.
 */
@ConfigurationProperties(prefix = "partvision.precios.egsa")
public record EgsaRecepcionProperties(
        String apiKey,
        /* Tal cual esta en configuracion_precios y en productos.proveedor. */
        @DefaultValue("EGSA") String proveedor,
        @DefaultValue("Código") String columnaCodigo,
        @DefaultValue("PrecioLista") String columnaPrecio,
        @DefaultValue("60") int maxSubaPct,
        @DefaultValue("35") int maxBajaPct,
        @DefaultValue("10") int maxSaltosPct,
        @DefaultValue("80") int minFilasPct,
        @DefaultValue("1000") int minFilas,
        /* Ojo: ~4% de la lista real de EGSA viene con precio en cero (importados, Sintermetal, Packson), por eso 20 y no 5. */
        @DefaultValue("20") int maxInvalidasPct,
        @DefaultValue("50") int minCoincidenciaPct,
        /* Codigos nuevos que se dan de alta solos de una vez; si llegan mas, no se crea ninguno. */
        @DefaultValue("300") int maxAltas,
        /* El robot corre todos los dias: sin lista en 2 dias se avisa. */
        @DefaultValue("2") int diasSinActualizar,
        @DefaultValue("26214400") long maxBytes
) {

    public boolean habilitada() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        // Nunca la clave en un log ni en un mensaje de error.
        return "EgsaRecepcionProperties[proveedor=" + proveedor + ", habilitada=" + habilitada() + "]";
    }
}
