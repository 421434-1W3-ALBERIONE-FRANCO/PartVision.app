package com.partvision.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Actualizacion automatica de precios de Autopartes del Sur desde su portal.
 *
 * <p>Queda apagada mientras no esten el usuario y la contraseña del portal (ADS_USUARIO /
 * ADS_PASSWORD en el entorno del servidor): sin ellos no hay forma de bajar la lista con los
 * precios de la cuenta del cliente, y la lista publica NO sirve de reemplazo porque no trae
 * sus descuentos (al 2026-09-30, ~2.090 productos con 2%, 10%, 15% o 20% menos).
 *
 * <p>Los umbrales son los frenos de una corrida que nadie mira: si la lista viene rara, no se
 * aplica y se avisa. Ver {@link SincronizacionPreciosService}.
 */
@ConfigurationProperties(prefix = "partvision.precios.ads")
public record AdsSyncProperties(
        String usuario,
        String password,
        @DefaultValue("https://catalogo.autopartesdelsur.com.ar/") String url,
        /* Tal cual esta en configuracion_precios y en productos.proveedor. */
        @DefaultValue("Autopartes del Sur") String proveedor,
        @DefaultValue("Código") String columnaCodigo,
        @DefaultValue("Precio de Lista") String columnaPrecio,
        /* Un producto que sube mas que esto de una corrida a otra queda para revisar. */
        @DefaultValue("60") int maxSubaPct,
        /* Idem si baja mas que esto. */
        @DefaultValue("35") int maxBajaPct,
        /* Si mas de este % de los precios comparables salta, la lista entera queda frenada. */
        @DefaultValue("10") int maxSaltosPct,
        /* Si trae menos de este % de filas que la ultima buena, queda frenada. */
        @DefaultValue("80") int minFilasPct,
        /* Por debajo de esta cantidad de filas con precio, la lista no es creible. */
        @DefaultValue("1000") int minFilas,
        /* Filas con precio ilegible o en cero por encima de este %: cambio de formato. */
        @DefaultValue("5") int maxInvalidasPct,
        /* Si menos de este % de los codigos existe en el catalogo, la columna esta corrida. */
        @DefaultValue("50") int minCoincidenciaPct,
        /* Sin una actualizacion buena en estos dias, se avisa aunque no haya errores. */
        @DefaultValue("3") int diasSinActualizar,
        @DefaultValue("20") int timeoutConexionSegundos,
        /* El portal arma el Excel en el momento: tarda. */
        @DefaultValue("300") int timeoutDescargaSegundos,
        @DefaultValue("41943040") long maxBytes
) {

    public boolean habilitada() {
        return usuario != null && !usuario.isBlank() && password != null && !password.isBlank();
    }

    /** La URL base siempre con la barra final, para poder concatenar las rutas de la API. */
    public String base() {
        return url.endsWith("/") ? url : url + "/";
    }

    @Override
    public String toString() {
        // Nunca la contraseña en un log ni en un mensaje de error.
        return "AdsSyncProperties[url=" + url + ", proveedor=" + proveedor
                + ", habilitada=" + habilitada() + "]";
    }
}
