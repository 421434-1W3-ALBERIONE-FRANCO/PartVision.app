package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.Tarifa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;

/**
 * Decide que hacer con cada fila de la lista del proveedor antes de tocar un solo precio.
 * No lee ni escribe la base: recibe lo que necesita y devuelve un {@link Analisis}.
 *
 * <p>Una fila mala (precio en cero, ilegible, codigo repetido con dos precios) no frena la
 * lista: se saltea y queda anotada. Lo que si la frena entera son las señales de que el
 * archivo vino mal armado, porque aplicado a 67 mil productos el daño seria enorme:
 * pocas filas, muchas ilegibles, codigos que no coinciden con el catalogo, o muchos precios
 * saltando de golpe.
 */
final class AnalizadorListaPrecios {

    /** Ningun repuesto cuesta esto: es un numero mal leido. */
    static final BigDecimal PRECIO_MAXIMO = new BigDecimal("1000000000");
    private static final int EJEMPLOS = 5;
    private static final int MIN_SALTOS_PARA_FRENAR = 20;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private AnalizadorListaPrecios() {}

    /** Lo que la corrida anterior dejo como referencia para comparar. */
    record Referencia(Integer filasLista) {
        static final Referencia NINGUNA = new Referencia(null);
    }

    record Umbrales(int maxSubaPct, int maxBajaPct, int maxSaltosPct, int minFilasPct,
                    int minFilas, int maxInvalidasPct, int minCoincidenciaPct) {
        static Umbrales de(AdsSyncProperties p) {
            return new Umbrales(p.maxSubaPct(), p.maxBajaPct(), p.maxSaltosPct(), p.minFilasPct(),
                    p.minFilas(), p.maxInvalidasPct(), p.minCoincidenciaPct());
        }
    }

    record Cambio(Producto producto, BigDecimal costo, BigDecimal venta) {}

    record Salto(Producto producto, BigDecimal precioLista, BigDecimal costo, BigDecimal variacionPct) {}

    record Analisis(
            List<Cambio> cambios,
            List<Salto> saltos,
            /* Filas con codigo y precio valido: lo que la lista trae de verdad. */
            int filasLista,
            int sinCambio,
            int noEncontrados,
            int invalidas,
            int repetidos,
            /* Lo que se saltea o se informa, sin frenar la lista. */
            List<String> problemas,
            /* Por que no se deberia aplicar la lista. Vacio = se puede aplicar. */
            List<String> motivosParaFrenar
    ) {
        boolean hayQueFrenar() {
            return !motivosParaFrenar.isEmpty();
        }
    }

    /**
     * @param filas            la lista de precios del proveedor
     * @param productosPorSku  el catalogo para esos codigos
     */
    static Analisis analizar(List<FilaArchivo> filas, Map<String, List<Producto>> productosPorSku, Tarifa tarifa,
                             String proveedor, Umbrales u, Referencia ref,
                             Function<String, BigDecimal> parsearPrecio) {

        // 1. Filas validas, una por codigo.
        Map<String, BigDecimal> precios = new LinkedHashMap<>();
        Set<String> repetidos = new LinkedHashSet<>();
        List<String> invalidas = new ArrayList<>();
        int conCodigo = 0;
        for (FilaArchivo f : filas) {
            String sku = f.sku() == null ? null : f.sku().trim();
            if (sku == null || sku.isEmpty()) continue;
            conCodigo++;
            BigDecimal precio = parsearPrecio.apply(f.precio());
            if (precio == null || precio.signum() <= 0 || precio.compareTo(PRECIO_MAXIMO) >= 0) {
                invalidas.add(sku);
                continue;
            }
            precio = precio.setScale(2, RoundingMode.HALF_UP);
            BigDecimal anterior = precios.putIfAbsent(sku, precio);
            if (anterior != null && anterior.compareTo(precio) != 0) repetidos.add(sku);
        }
        repetidos.forEach(precios::remove);

        // 2. Cada precio contra el catalogo.
        List<Cambio> cambios = new ArrayList<>();
        List<Salto> saltos = new ArrayList<>();
        List<String> noEncontrados = new ArrayList<>();
        int sinCambio = 0, comparables = 0;
        BigDecimal maxSuba = BigDecimal.valueOf(u.maxSubaPct());
        BigDecimal maxBaja = BigDecimal.valueOf(u.maxBajaPct()).negate();

        for (Map.Entry<String, BigDecimal> e : precios.entrySet()) {
            Producto p = delProveedor(productosPorSku.getOrDefault(e.getKey(), List.of()), proveedor);
            if (p == null) {
                noEncontrados.add(e.getKey());
                continue;
            }
            BigDecimal costo = tarifa.costoDesde(e.getValue());
            BigDecimal venta = tarifa.ventaDesde(costo);
            BigDecimal costoActual = p.getPrecioCosto();
            if (mismo(costoActual, costo) && mismo(p.getPrecioVenta(), venta)) {
                sinCambio++;
                comparables++;
                continue;
            }
            if (costoActual == null || costoActual.signum() <= 0) {
                // Primer precio: no hay contra que comparar.
                cambios.add(new Cambio(p, costo, venta));
                continue;
            }
            comparables++;
            BigDecimal variacion = costo.subtract(costoActual).multiply(CIEN)
                    .divide(costoActual, 2, RoundingMode.HALF_UP);
            if (variacion.compareTo(maxSuba) > 0 || variacion.compareTo(maxBaja) < 0) {
                saltos.add(new Salto(p, e.getValue(), costo, variacion));
            } else {
                cambios.add(new Cambio(p, costo, venta));
            }
        }

        // 3. Lo que se informa y lo que frena.
        int filasLista = precios.size();
        List<String> problemas = new ArrayList<>();
        if (!invalidas.isEmpty()) {
            problemas.add(n(invalidas.size()) + " producto(s) vienen con precio en cero o ilegible: se dejaron como estaban"
                    + ejemplos(invalidas) + ".");
        }
        if (!repetidos.isEmpty()) {
            problemas.add(n(repetidos.size()) + " código(s) aparecen repetidos con precios distintos: no se tocaron"
                    + ejemplos(repetidos) + ".");
        }
        if (!noEncontrados.isEmpty()) {
            problemas.add(n(noEncontrados.size()) + " código(s) de la lista no están en tu catálogo como " + proveedor
                    + ejemplos(noEncontrados) + ". Si los querés, dalos de alta desde la importación manual.");
        }
        if (!saltos.isEmpty()) {
            problemas.add(n(saltos.size()) + " precio(s) cambian más de lo normal (suben más de " + u.maxSubaPct()
                    + "% o bajan más de " + u.maxBajaPct() + "%): no se aplicaron, quedaron para que los revises.");
        }

        List<String> frenos = new ArrayList<>();
        if (filasLista < u.minFilas()) {
            frenos.add("La lista trae solo " + n(filasLista) + " productos con precio (se esperan más de "
                    + n(u.minFilas()) + ").");
        } else if (ref.filasLista() != null && filasLista * 100L < (long) ref.filasLista() * u.minFilasPct()) {
            frenos.add("La lista trae " + n(filasLista) + " productos con precio y la última traía "
                    + n(ref.filasLista()) + ": faltan demasiados.");
        }
        if (conCodigo > 0 && invalidas.size() * 100L > (long) conCodigo * u.maxInvalidasPct()) {
            frenos.add(n(invalidas.size()) + " de " + n(conCodigo)
                    + " filas tienen el precio en cero o ilegible: puede que ADS haya cambiado el formato.");
        }
        int coincidencias = filasLista - noEncontrados.size();
        if (filasLista > 0 && coincidencias * 100L < (long) filasLista * u.minCoincidenciaPct()) {
            frenos.add("Solo " + n(coincidencias) + " de " + n(filasLista)
                    + " códigos coinciden con tu catálogo: puede que las columnas vengan corridas.");
        }
        if (saltos.size() >= MIN_SALTOS_PARA_FRENAR && saltos.size() * 100L > (long) comparables * u.maxSaltosPct()) {
            frenos.add(n(saltos.size()) + " de " + n(comparables)
                    + " precios cambian más de lo normal a la vez: puede ser un error en la lista de ADS.");
        }

        return new Analisis(cambios, saltos, filasLista, sinCambio, noEncontrados.size(), invalidas.size(),
                repetidos.size(), problemas, frenos);
    }

    /**
     * Solo se tocan productos cargados con este proveedor. La importacion manual acepta un
     * candidato unico aunque sea de otro proveedor (asi EGSA termino poniendole precio a
     * productos de ADS); una corrida que nadie mira no puede darse ese lujo.
     */
    private static Producto delProveedor(List<Producto> candidatos, String proveedor) {
        for (Producto p : candidatos) {
            if (p.getProveedor() != null && p.getProveedor().trim().equalsIgnoreCase(proveedor.trim())) return p;
        }
        return null;
    }

    private static boolean mismo(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private static String ejemplos(Collection<String> skus) {
        StringJoiner j = new StringJoiner(", ", " (ej: ", skus.size() > EJEMPLOS ? "…)" : ")");
        // Si las columnas vienen corridas, el "codigo" puede ser una descripcion entera.
        skus.stream().limit(EJEMPLOS).map(x -> x.length() > 30 ? x.substring(0, 30) + "…" : x).forEach(j::add);
        return j.toString();
    }

    static String n(long numero) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR")).format(numero);
    }
}
