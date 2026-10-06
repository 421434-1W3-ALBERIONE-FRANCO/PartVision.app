package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.Tarifa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
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
 *
 * <p>EGSA usa el mismo codigo para productos distintos de marcas distintas (el 106 es un arbol
 * de levas BH y una cadena RUL-REP) y PartVision guarda un solo producto por proveedor y codigo.
 * Cuando un codigo viene repetido con precios distintos, el precio del producto es el de la
 * fila que trae SU marca; si no hay forma de saberlo, no se toca.
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

    /** @param maxAltas codigos nuevos que se dan de alta solos de una vez; 0 = no se dan de alta */
    record Umbrales(int maxSubaPct, int maxBajaPct, int maxSaltosPct, int minFilasPct,
                    int minFilas, int maxInvalidasPct, int minCoincidenciaPct, int maxAltas) {

        Umbrales(int maxSubaPct, int maxBajaPct, int maxSaltosPct, int minFilasPct,
                 int minFilas, int maxInvalidasPct, int minCoincidenciaPct) {
            this(maxSubaPct, maxBajaPct, maxSaltosPct, minFilasPct, minFilas, maxInvalidasPct, minCoincidenciaPct, 0);
        }

        static Umbrales de(AdsSyncProperties p) {
            return new Umbrales(p.maxSubaPct(), p.maxBajaPct(), p.maxSaltosPct(), p.minFilasPct(),
                    p.minFilas(), p.maxInvalidasPct(), p.minCoincidenciaPct(), 0);
        }

        static Umbrales de(EgsaRecepcionProperties p) {
            return new Umbrales(p.maxSubaPct(), p.maxBajaPct(), p.maxSaltosPct(), p.minFilasPct(),
                    p.minFilas(), p.maxInvalidasPct(), p.minCoincidenciaPct(), p.maxAltas());
        }
    }

    record Cambio(Producto producto, BigDecimal costo, BigDecimal venta) {}

    record Salto(Producto producto, BigDecimal precioLista, BigDecimal costo, BigDecimal variacionPct) {}

    /** Un codigo de la lista que no existe en el catalogo, con su precio ya leido. */
    record Nuevo(String sku, BigDecimal precioLista) {}

    record Analisis(
            List<Cambio> cambios,
            List<Salto> saltos,
            List<Nuevo> nuevos,
            /* Filas con codigo y precio valido: lo que la lista trae de verdad. */
            int filasLista,
            int sinCambio,
            int noEncontrados,
            int invalidas,
            int repetidos,
            /* Codigos repetidos con marcas distintas que se resolvieron con la marca del producto. */
            int repetidosPorMarca,
            /* Lo que se saltea o se informa, sin frenar la lista. */
            List<String> problemas,
            /* Por que no se deberia aplicar la lista. Vacio = se puede aplicar. */
            List<String> motivosParaFrenar
    ) {
        boolean hayQueFrenar() {
            return !motivosParaFrenar.isEmpty();
        }
    }

    /** Una fila con codigo y precio validos. */
    private record Valida(BigDecimal precio, String marca) {}

    /**
     * @param filas            la lista de precios del proveedor
     * @param productosPorSku  el catalogo para esos codigos
     */
    static Analisis analizar(List<FilaArchivo> filas, Map<String, List<Producto>> productosPorSku, Tarifa tarifa,
                             String proveedor, Umbrales u, Referencia ref,
                             Function<String, BigDecimal> parsearPrecio) {

        // 1. Filas validas, agrupadas por codigo.
        Map<String, List<Valida>> porSku = new LinkedHashMap<>();
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
            porSku.computeIfAbsent(sku, k -> new ArrayList<>(1))
                    .add(new Valida(precio.setScale(2, RoundingMode.HALF_UP), f.marca()));
        }

        // 2. Cada precio contra el catalogo.
        List<Cambio> cambios = new ArrayList<>();
        List<Salto> saltos = new ArrayList<>();
        List<Nuevo> nuevos = new ArrayList<>();
        Set<String> repetidos = new LinkedHashSet<>();
        int repetidosPorMarca = 0;
        int sinCambio = 0, comparables = 0;
        BigDecimal maxSuba = BigDecimal.valueOf(u.maxSubaPct());
        BigDecimal maxBaja = BigDecimal.valueOf(u.maxBajaPct()).negate();

        for (Map.Entry<String, List<Valida>> e : porSku.entrySet()) {
            String sku = e.getKey();
            Producto p = delProveedor(productosPorSku.getOrDefault(sku, List.of()), proveedor);

            BigDecimal precioLista;
            if (mismoPrecio(e.getValue())) {
                precioLista = e.getValue().getFirst().precio();
            } else {
                precioLista = p == null ? null : precioDeSuMarca(p, e.getValue());
                if (precioLista == null) {
                    repetidos.add(sku);
                    continue;
                }
                repetidosPorMarca++;
            }

            if (p == null) {
                nuevos.add(new Nuevo(sku, precioLista));
                continue;
            }
            BigDecimal costo = tarifa.costoDesde(precioLista);
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
                saltos.add(new Salto(p, precioLista, costo, variacion));
            } else {
                cambios.add(new Cambio(p, costo, venta));
            }
        }

        // 3. Lo que se informa y lo que frena.
        int filasLista = porSku.size() - repetidos.size();
        List<String> problemas = new ArrayList<>();
        if (!invalidas.isEmpty()) {
            problemas.add(n(invalidas.size()) + " producto(s) vienen con precio en cero o ilegible: se dejaron como estaban"
                    + ejemplos(invalidas) + ".");
        }
        if (!repetidos.isEmpty()) {
            problemas.add(n(repetidos.size()) + " código(s) aparecen repetidos con precios distintos y no se pudo saber "
                    + "cuál es de cada producto: no se tocaron" + ejemplos(repetidos) + ".");
        }
        if (repetidosPorMarca > 0) {
            problemas.add(n(repetidosPorMarca) + " código(s) vienen repetidos con marcas distintas: "
                    + "se usó el precio de la marca de cada producto.");
        }
        // Si la fuente da de alta sola, el aplicador cuenta que paso con los nuevos.
        if (!nuevos.isEmpty() && u.maxAltas() == 0) {
            problemas.add(n(nuevos.size()) + " código(s) de la lista no están en tu catálogo como " + proveedor
                    + ejemplos(nuevos.stream().map(Nuevo::sku).toList())
                    + ". Si los querés, dalos de alta desde la importación manual.");
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
                    + " filas tienen el precio en cero o ilegible: puede que el proveedor haya cambiado el formato.");
        }
        int coincidencias = filasLista - nuevos.size();
        if (filasLista > 0 && coincidencias * 100L < (long) filasLista * u.minCoincidenciaPct()) {
            frenos.add("Solo " + n(coincidencias) + " de " + n(filasLista)
                    + " códigos coinciden con tu catálogo: puede que las columnas vengan corridas.");
        }
        if (saltos.size() >= MIN_SALTOS_PARA_FRENAR && saltos.size() * 100L > (long) comparables * u.maxSaltosPct()) {
            frenos.add(n(saltos.size()) + " de " + n(comparables)
                    + " precios cambian más de lo normal a la vez: puede ser un error en la lista del proveedor.");
        }

        return new Analisis(cambios, saltos, nuevos, filasLista, sinCambio, nuevos.size(), invalidas.size(),
                repetidos.size(), repetidosPorMarca, problemas, frenos);
    }

    /**
     * Solo se tocan productos cargados con este proveedor. La importacion manual acepta un
     * candidato unico aunque sea de otro proveedor (asi EGSA termino poniendole precio a
     * productos de ADS); una corrida que nadie mira no puede darse ese lujo.
     */
    static Producto delProveedor(List<Producto> candidatos, String proveedor) {
        for (Producto p : candidatos) {
            if (p.getProveedor() != null && p.getProveedor().trim().equalsIgnoreCase(proveedor.trim())) return p;
        }
        return null;
    }

    private static boolean mismoPrecio(List<Valida> filas) {
        BigDecimal primero = filas.getFirst().precio();
        for (Valida v : filas) {
            if (v.precio().compareTo(primero) != 0) return false;
        }
        return true;
    }

    /**
     * El precio de la fila que trae la marca del producto, si hay una sola respuesta posible.
     * Si el producto no tiene marca, la lista no la trae, o hay dos filas de su marca con
     * precios distintos, devuelve null: mejor no tocarlo que ponerle el precio de otro.
     */
    private static BigDecimal precioDeSuMarca(Producto p, List<Valida> filas) {
        String marca = p.getMarca() == null ? null : normalizarMarca(p.getMarca().getNombre());
        if (marca == null) return null;
        BigDecimal precio = null;
        for (Valida v : filas) {
            if (!marca.equals(normalizarMarca(v.marca()))) continue;
            if (precio != null && precio.compareTo(v.precio()) != 0) return null;
            precio = v.precio();
        }
        return precio;
    }

    /** Sin mayusculas, tildes ni espacios de mas; null si no queda nada. */
    static String normalizarMarca(String marca) {
        if (marca == null) return null;
        String limpia = Normalizer.normalize(marca, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
        return limpia.isEmpty() ? null : limpia;
    }

    private static boolean mismo(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    static String ejemplos(Collection<String> skus) {
        StringJoiner j = new StringJoiner(", ", " (ej: ", skus.size() > EJEMPLOS ? "…)" : ")");
        // Si las columnas vienen corridas, el "codigo" puede ser una descripcion entera.
        skus.stream().limit(EJEMPLOS).map(x -> x.length() > 30 ? x.substring(0, 30) + "…" : x).forEach(j::add);
        return j.toString();
    }

    static String n(long numero) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR")).format(numero);
    }
}
