package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.pricing.AnalizadorListaPrecios.Analisis;
import com.partvision.pricing.AnalizadorListaPrecios.Referencia;
import com.partvision.pricing.AnalizadorListaPrecios.Umbrales;
import com.partvision.pricing.PrecioImportService.FilaArchivo;
import com.partvision.pricing.PrecioImportService.Tarifa;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AnalizadorListaPreciosTest {

    private static final String ADS = "Autopartes del Sur";
    /** Umbrales chicos para poder probar con pocas filas. */
    private static final Umbrales U = new Umbrales(60, 35, 10, 80, 1, 5, 50);
    /** Sin ajuste de lista y 10% de margen: costo = precio del archivo, venta = costo * 1,1. */
    private static final Tarifa TARIFA = new Tarifa(new BigDecimal("10"), BigDecimal.ZERO);

    private final PrecioImportService parser = new PrecioImportService(null, null, null, null, null);
    private final Map<String, List<Producto>> catalogo = new HashMap<>();

    private Producto producto(String sku, String proveedor, String costo) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setProveedor(proveedor);
        if (costo != null) {
            p.setPrecioCosto(new BigDecimal(costo));
            p.setPrecioVenta(TARIFA.ventaDesde(new BigDecimal(costo)));
        }
        catalogo.computeIfAbsent(sku, k -> new ArrayList<>()).add(p);
        return p;
    }

    private Producto producto(String sku, String proveedor, String costo, String marca) {
        Producto p = producto(sku, proveedor, costo);
        if (marca != null) {
            Marca m = new Marca();
            m.setNombre(marca);
            p.setMarca(m);
        }
        return p;
    }

    private static FilaArchivo filaConMarca(String sku, String precio, String marca) {
        return new FilaArchivo(sku, precio, "desc", marca);
    }

    private static FilaArchivo fila(String sku, String precio) {
        return new FilaArchivo(sku, precio, null, null);
    }

    private Analisis analizar(List<FilaArchivo> filas, Umbrales u, Referencia ref) {
        return AnalizadorListaPrecios.analizar(filas, catalogo, TARIFA, ADS, u, ref, parser::parsearPrecio);
    }

    private Analisis analizar(List<FilaArchivo> filas) {
        return analizar(filas, U, Referencia.NINGUNA);
    }

    @Test
    void precioQueCambiaPoco_seAplicaConCostoYVenta() {
        Producto p = producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "110")));

        assertThat(a.cambios()).hasSize(1);
        assertThat(a.cambios().getFirst().producto()).isSameAs(p);
        assertThat(a.cambios().getFirst().costo()).isEqualByComparingTo("110.00");
        assertThat(a.cambios().getFirst().venta()).isEqualByComparingTo("121.00");
        assertThat(a.saltos()).isEmpty();
        assertThat(a.hayQueFrenar()).isFalse();
        assertThat(a.problemas()).isEmpty();
    }

    @Test
    void mismoPrecio_cuentaComoSinCambio() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100")));

        assertThat(a.cambios()).isEmpty();
        assertThat(a.sinCambio()).isEqualTo(1);
    }

    @Test
    void mismoCostoPeroOtraVenta_esUnCambio() {
        // El margen cambio desde la ultima vez: la venta guardada ya no corresponde.
        Producto p = producto("A1", ADS, "100.00");
        p.setPrecioVenta(new BigDecimal("105.00"));

        Analisis a = analizar(List.of(fila("A1", "100")));

        assertThat(a.cambios()).hasSize(1);
        assertThat(a.cambios().getFirst().venta()).isEqualByComparingTo("110.00");
    }

    @Test
    void productoSinPrecioPrevio_seAplicaSinCompararAunqueSeaAlto() {
        producto("A1", ADS, null);
        producto("A2", ADS, "0");

        Analisis a = analizar(List.of(fila("A1", "5000"), fila("A2", "7000")));

        assertThat(a.cambios()).hasSize(2);
        assertThat(a.saltos()).isEmpty();
    }

    @Test
    void subaGrande_quedaParaRevisarConSuVariacion() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "170")));

        assertThat(a.cambios()).isEmpty();
        assertThat(a.saltos()).hasSize(1);
        assertThat(a.saltos().getFirst().variacionPct()).isEqualByComparingTo("70.00");
        assertThat(a.saltos().getFirst().precioLista()).isEqualByComparingTo("170");
        assertThat(a.problemas()).anyMatch(p -> p.contains("cambian más de lo normal"));
    }

    @Test
    void bajaGrande_quedaParaRevisar_yJustoEnElLimiteSeAplica() {
        producto("A1", ADS, "100.00");
        producto("A2", ADS, "100.00");
        producto("A3", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "60"), fila("A2", "65"), fila("A3", "160")));

        assertThat(a.saltos()).extracting(s -> s.producto().getSku()).containsExactly("A1");
        assertThat(a.cambios()).extracting(c -> c.producto().getSku()).containsExactly("A2", "A3");
    }

    @Test
    void preciosInvalidos_seSalteanYSeInforman() {
        producto("A1", ADS, "100.00");
        producto("A2", ADS, "100.00");
        producto("A3", ADS, "100.00");
        producto("A4", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "0"), fila("A2", "abc"), fila("A3", null),
                fila("A4", "1000000000"), fila(" ", "10"), fila(null, "10")),
                new Umbrales(60, 35, 10, 80, 0, 100, 0), Referencia.NINGUNA);

        assertThat(a.invalidas()).isEqualTo(4);
        assertThat(a.cambios()).isEmpty();
        assertThat(a.problemas().getFirst()).contains("4 producto(s)").contains("precio en cero o ilegible")
                .contains("(ej: A1, A2, A3, A4)");
    }

    @Test
    void codigoRepetido_conDistintoPrecioNoSeToca_conElMismoSeUsaUnaVez() {
        producto("A1", ADS, "100.00");
        producto("A2", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "110"), fila("A1", "120"), fila("A2", "105"), fila("A2", "105")));

        assertThat(a.repetidos()).isEqualTo(1);
        assertThat(a.cambios()).extracting(c -> c.producto().getSku()).containsExactly("A2");
        assertThat(a.filasLista()).isEqualTo(1);
        assertThat(a.problemas()).anyMatch(p -> p.contains("repetidos con precios distintos") && p.contains("A1"));
    }

    @Test
    void soloTocaProductosDelProveedor() {
        producto("A1", "EGSA", "100.00");
        producto("A2", null, "100.00");
        Producto deEgsa = producto("A3", "EGSA", "100.00");
        producto("A3", " autopartes del sur ", "100.00");

        Analisis a = analizar(List.of(fila("A1", "110"), fila("A2", "110"), fila("A3", "110")),
                new Umbrales(60, 35, 10, 80, 1, 5, 0), Referencia.NINGUNA);

        assertThat(a.cambios()).hasSize(1);
        assertThat(a.cambios().getFirst().producto()).isNotSameAs(deEgsa);
        assertThat(a.cambios().getFirst().producto().getProveedor()).isEqualTo(" autopartes del sur ");
        assertThat(a.noEncontrados()).isEqualTo(2);
        assertThat(a.problemas()).anyMatch(p -> p.contains("no están en tu catálogo como Autopartes del Sur"));
    }

    @Test
    void ejemplos_seCortanEnCinco() {
        List<FilaArchivo> filas = new ArrayList<>();
        for (int i = 1; i <= 7; i++) filas.add(fila("X" + i, "10"));

        Analisis a = analizar(filas, new Umbrales(60, 35, 10, 80, 1, 5, 0), Referencia.NINGUNA);

        assertThat(a.problemas().getFirst()).contains("(ej: X1, X2, X3, X4, X5…)");
    }

    @Test
    void ejemplos_largosSeRecortan() {
        Analisis a = analizar(List.of(fila("PISTONES FIAT - 600 - 750CC (+0.80MM)(62.80MM)", "10")),
                new Umbrales(60, 35, 10, 80, 1, 5, 0), Referencia.NINGUNA);

        assertThat(a.problemas().getFirst()).contains("(ej: PISTONES FIAT - 600 - 750CC (+…)");
    }

    @Test
    void frena_pocasFilas() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100")), new Umbrales(60, 35, 10, 80, 1000, 5, 50),
                Referencia.NINGUNA);

        assertThat(a.motivosParaFrenar()).singleElement().asString().contains("solo 1 productos con precio");
    }

    @Test
    void frena_muchasMenosFilasQueLaUltima() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100")), U, new Referencia(67_280));

        assertThat(a.motivosParaFrenar()).singleElement().asString().contains("la última traía 67.280");
    }

    @Test
    void noFrena_siTraeAlMenosElMinimoDeLaUltima() {
        producto("A1", ADS, "100.00");
        producto("A2", ADS, "100.00");
        producto("A3", ADS, "100.00");
        producto("A4", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100"), fila("A2", "100"), fila("A3", "100"), fila("A4", "100")),
                U, new Referencia(5));

        assertThat(a.hayQueFrenar()).isFalse();
    }

    @Test
    void frena_muchasFilasIlegibles() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100"), fila("A2", "x")),
                new Umbrales(60, 35, 10, 80, 1, 5, 0), Referencia.NINGUNA);

        assertThat(a.motivosParaFrenar()).singleElement().asString().contains("1 de 2 filas");
    }

    @Test
    void frena_codigosQueNoCoincidenConElCatalogo() {
        producto("A1", ADS, "100.00");

        Analisis a = analizar(List.of(fila("A1", "100"), fila("Filtro de aceite", "5"), fila("Correa", "7")));

        assertThat(a.motivosParaFrenar()).singleElement().asString().contains("Solo 1 de 3 códigos");
    }

    @Test
    void frena_muchosSaltosALaVez() {
        List<FilaArchivo> filas = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            producto("S" + i, ADS, "100.00");
            filas.add(fila("S" + i, "10000")); // precio en centavos: todo salta x100
        }

        Analisis a = analizar(filas);

        assertThat(a.saltos()).hasSize(25);
        assertThat(a.motivosParaFrenar()).singleElement().asString().contains("25 de 25 precios");
    }

    @Test
    void noFrena_pocosSaltosAunqueSeanTodos() {
        List<FilaArchivo> filas = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            producto("S" + i, ADS, "100.00");
            filas.add(fila("S" + i, "10000"));
        }

        Analisis a = analizar(filas);

        assertThat(a.saltos()).hasSize(19);
        assertThat(a.hayQueFrenar()).isFalse();
    }

    @Test
    void noFrena_saltosQueSonPocosEntreMuchos() {
        List<FilaArchivo> filas = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            producto("P" + i, ADS, "100.00");
            filas.add(fila("P" + i, i < 25 ? "10000" : "105"));
        }

        Analisis a = analizar(filas);

        assertThat(a.saltos()).hasSize(25);
        assertThat(a.cambios()).hasSize(275);
        assertThat(a.hayQueFrenar()).isFalse();
    }

    @Test
    void listaVacia_noRompe() {
        Analisis a = analizar(List.of(), new Umbrales(60, 35, 10, 80, 0, 5, 50), Referencia.NINGUNA);

        assertThat(a.filasLista()).isZero();
        assertThat(a.hayQueFrenar()).isFalse();
    }

    @Test
    void numeros_conSeparadorDeMiles() {
        assertThat(AnalizadorListaPrecios.n(67280)).isEqualTo("67.280");
    }

    // --- Codigos repetidos con marcas distintas (EGSA) ---

    @Test
    void repetidoConMarcasDistintas_usaElPrecioDeLaMarcaDelProducto() {
        Producto arbol = producto("106", ADS, "100.00", "BH");

        Analisis a = analizar(List.of(filaConMarca("106", "18736.64", "RUL-REP"), filaConMarca("106", "110", "BH")));

        assertThat(a.cambios()).singleElement().satisfies(c -> {
            assertThat(c.producto()).isSameAs(arbol);
            assertThat(c.costo()).isEqualByComparingTo("110.00");
        });
        assertThat(a.repetidos()).isZero();
        assertThat(a.repetidosPorMarca()).isEqualTo(1);
        assertThat(a.filasLista()).isEqualTo(1);
        assertThat(a.problemas()).anyMatch(p -> p.contains("1 código(s) vienen repetidos con marcas distintas"));
    }

    @Test
    void repetido_laMarcaSeComparaSinMayusculasTildesNiEspaciosDeMas() {
        producto("X1", ADS, "100.00", "Rul  Rép");

        Analisis a = analizar(List.of(filaConMarca("X1", "999", "BH"), filaConMarca("X1", "105", " RUL REP ")));

        assertThat(a.cambios()).singleElement().satisfies(c -> assertThat(c.costo()).isEqualByComparingTo("105.00"));
    }

    @Test
    void repetido_siElProductoNoTieneMarca_noSeToca() {
        producto("X1", ADS, "100.00");

        Analisis a = analizar(List.of(filaConMarca("X1", "110", "BH"), filaConMarca("X1", "120", "RUL-REP")));

        assertThat(a.cambios()).isEmpty();
        assertThat(a.repetidos()).isEqualTo(1);
        assertThat(a.repetidosPorMarca()).isZero();
    }

    @Test
    void repetido_siNingunaFilaTraeSuMarca_noSeToca() {
        producto("X1", ADS, "100.00", "STACO");

        Analisis a = analizar(List.of(filaConMarca("X1", "110", "BH"), filaConMarca("X1", "120", "RUL-REP")));

        assertThat(a.cambios()).isEmpty();
        assertThat(a.repetidos()).isEqualTo(1);
    }

    @Test
    void repetido_siLaListaNoTraeMarca_noSeToca() {
        producto("X1", ADS, "100.00", "BH");

        Analisis a = analizar(List.of(fila("X1", "110"), fila("X1", "120")));

        assertThat(a.repetidos()).isEqualTo(1);
    }

    @Test
    void repetido_dosFilasDeSuMarcaConPreciosDistintos_noSeToca() {
        producto("4015300", ADS, "100.00", "EMIC");

        Analisis a = analizar(List.of(filaConMarca("4015300", "4824.66", "EMIC"), filaConMarca("4015300", "5469.33", "EMIC")));

        assertThat(a.cambios()).isEmpty();
        assertThat(a.repetidos()).isEqualTo(1);
    }

    @Test
    void repetido_dosFilasDeSuMarcaConElMismoPrecio_seUsaEseYCuentaComoResuelto() {
        producto("X1", ADS, "100.00", "BH");

        Analisis a = analizar(List.of(filaConMarca("X1", "110", "BH"), filaConMarca("X1", "110", "BH"),
                filaConMarca("X1", "999", "OTRA")));

        assertThat(a.cambios()).singleElement().satisfies(c -> assertThat(c.costo()).isEqualByComparingTo("110.00"));
        assertThat(a.repetidosPorMarca()).isEqualTo(1);
    }

    @Test
    void repetido_sinProductoEnElCatalogo_noSeDaDeAltaNiSeCuentaComoNuevo() {
        Analisis a = analizar(List.of(filaConMarca("Z9", "110", "BH"), filaConMarca("Z9", "120", "RUL-REP")));

        assertThat(a.repetidos()).isEqualTo(1);
        assertThat(a.nuevos()).isEmpty();
    }

    @Test
    void repetidoConElMismoPrecioEnTodasLasFilas_noEsUnProblema() {
        producto("X1", ADS, "100.00");

        Analisis a = analizar(List.of(filaConMarca("X1", "110", "BH"), filaConMarca("X1", "110", "RUL-REP")));

        assertThat(a.repetidos()).isZero();
        assertThat(a.repetidosPorMarca()).isZero();
        assertThat(a.cambios()).hasSize(1);
    }

    // --- Codigos nuevos ---

    @Test
    void nuevos_seDevuelvenConSuPrecio_yEnADSSeAvisaDeLaImportacionManual() {
        Analisis a = analizar(List.of(fila("N1", "50"), fila("N2", "60")), new Umbrales(60, 35, 10, 80, 1, 5, 0), Referencia.NINGUNA);

        assertThat(a.nuevos()).extracting(AnalizadorListaPrecios.Nuevo::sku).containsExactly("N1", "N2");
        assertThat(a.nuevos().getFirst().precioLista()).isEqualByComparingTo("50.00");
        assertThat(a.noEncontrados()).isEqualTo(2);
        assertThat(a.problemas()).singleElement().asString().contains("no están en tu catálogo").contains("importación manual");
    }

    @Test
    void nuevos_siLaFuenteDaDeAltaSola_elAnalizadorNoDiceNada() {
        Analisis a = analizar(List.of(fila("N1", "50")), new Umbrales(60, 35, 10, 80, 1, 5, 0, 300), Referencia.NINGUNA);

        assertThat(a.nuevos()).hasSize(1);
        assertThat(a.problemas()).isEmpty();
    }

    @Test
    void normalizarMarca() {
        assertThat(AnalizadorListaPrecios.normalizarMarca("  Rul-Rép  ")).isEqualTo("RUL-REP");
        assertThat(AnalizadorListaPrecios.normalizarMarca("a   b")).isEqualTo("A B");
        assertThat(AnalizadorListaPrecios.normalizarMarca("   ")).isNull();
        assertThat(AnalizadorListaPrecios.normalizarMarca(null)).isNull();
    }

    @Test
    void umbrales_desdeLasPropiedades() {
        assertThat(Umbrales.de(SincronizacionPreciosServiceTest.egsaProps()).maxAltas()).isEqualTo(300);
        assertThat(Umbrales.de(new AdsSyncProperties("u", "p", "http://a/", "ADS", "c", "p", 60, 35, 10, 80, 1000, 5, 50, 3, 5, 5, 1))
                .maxAltas()).isZero();
    }
}
