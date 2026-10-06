package com.partvision.pricing;

import com.partvision.catalog.domain.Marca;
import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.imports.service.ImportJob;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.imports.service.ProductoImporter;
import com.partvision.pricing.domain.*;
import com.partvision.pricing.dto.SincronizacionResponse;
import com.partvision.pricing.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecepcionListaServiceTest {

    private static final String EGSA = "EGSA";

    @Mock private ProductoRepository productoRepository;
    @Mock private ConfiguracionPrecioRepository configuracionRepo;
    @Mock private ImportPrecioBatchRepository batchRepo;
    @Mock private HistorialPrecioRepository historialRepo;
    @Mock private SincronizacionPrecioRepository syncRepo;
    @Mock private PrecioRevisionRepository revisionRepo;
    @Mock private ListasRetenidas retenidas;
    @Mock private ProductoBulkImporter bulkImporter;

    private PrecioImportService importService;
    private RecepcionListaService service;
    private final Map<Long, SincronizacionPrecio> guardadas = new LinkedHashMap<>();
    private final List<Producto> catalogo = new ArrayList<>();
    private Executor executor = Runnable::run;

    @BeforeEach
    void setUp() {
        importService = new PrecioImportService(productoRepository, configuracionRepo, batchRepo, historialRepo, bulkImporter);
        EgsaRecepcionProperties egsa = SincronizacionPreciosServiceTest.egsaProps();
        AdsSyncProperties ads = new AdsSyncProperties("u", "p", "http://ads.test/", "Autopartes del Sur", "Código",
                "Precio de Lista", 60, 35, 10, 80, 1, 5, 50, 3, 5, 5, 1000);
        FuentesListas fuentes = new FuentesListas(ads, egsa);
        AplicadorListaPrecios aplicador = new AplicadorListaPrecios(importService, productoRepository, bulkImporter,
                batchRepo, historialRepo, syncRepo, revisionRepo, fuentes);
        service = new RecepcionListaService(egsa, importService, aplicador, syncRepo, retenidas, fuentes,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), r -> executor.execute(r));

        ConfiguracionPrecio config = new ConfiguracionPrecio();
        config.setProveedor(EGSA);
        config.setMargen(new BigDecimal("10"));
        config.setAjusteLista(BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase(EGSA)).thenReturn(Optional.of(config));

        when(syncRepo.save(any())).thenAnswer(inv -> {
            SincronizacionPrecio s = inv.getArgument(0);
            if (s.getId() == null) s.setId((long) guardadas.size() + 1);
            guardadas.put(s.getId(), s);
            return s;
        });
        when(syncRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(guardadas.get(inv.<Long>getArgument(0))));
        when(batchRepo.save(any())).thenAnswer(inv -> {
            ImportPrecioBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(77L);
            return b;
        });
        when(productoRepository.findBySkuIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> skus = inv.getArgument(0);
            return catalogo.stream().filter(p -> skus.contains(p.getSku())).toList();
        });
    }

    private Producto producto(String sku, String costo, String marca) {
        Producto p = new Producto();
        p.setId((long) catalogo.size() + 1);
        p.setSku(sku);
        p.setDescripcion("Repuesto " + sku);
        p.setProveedor(EGSA);
        if (costo != null) {
            p.setPrecioCosto(new BigDecimal(costo));
            p.setPrecioVenta(new BigDecimal(costo).multiply(new BigDecimal("1.1")).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (marca != null) {
            Marca m = new Marca();
            m.setNombre(marca);
            p.setMarca(m);
        }
        catalogo.add(p);
        return p;
    }

    /** Como la que exporta EGSA CAT: Producto, Marca, Código, Descripción, PrecioLista, CostoNeto. */
    private static byte[] excel(Object[]... filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Sheet1");
            Row h = sh.createRow(0);
            String[] titulos = {"Producto", "Marca", "Código", "Descripción", "PrecioLista", "CostoNeto"};
            for (int i = 0; i < titulos.length; i++) h.createCell(i).setCellValue(titulos[i]);
            for (int i = 0; i < filas.length; i++) {
                Row r = sh.createRow(i + 1);
                r.createCell(0).setCellValue("RUBRO");
                r.createCell(1).setCellValue((String) filas[i][0]);
                r.createCell(2).setCellValue((String) filas[i][1]);
                r.createCell(3).setCellValue((String) filas[i][2]);
                if (filas[i][3] instanceof Number n) r.createCell(4).setCellValue(n.doubleValue());
                else r.createCell(4).setCellValue((String) filas[i][3]);
                r.createCell(5).setCellValue(1);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object[] fila(String marca, String codigo, String descripcion, Object precio) {
        return new Object[]{marca, codigo, descripcion, precio};
    }

    private SincronizacionPrecio ultima() {
        return guardadas.values().stream().reduce((a, b) -> b).orElseThrow();
    }

    // --- recibir ---

    @Test
    void recibe_aplicaLoQueCambio_yLiberaElCandado() {
        Producto sube = producto("A1", "100.00", "BH");
        producto("A2", "200.00", "BH");

        SincronizacionResponse r = service.recibir(excel(
                fila("BH", "A1", "Arbol", 110), fila("BH", "A2", "Otro", 200)), "EGSA_ListaPrecios_05-10-2026.xlsx");

        assertThat(r.origen()).isEqualTo("RECEPCION");
        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(s.getOrigen()).isEqualTo(OrigenSincronizacion.RECEPCION);
        assertThat(s.getProveedor()).isEqualTo(EGSA);
        assertThat(s.getActualizados()).isEqualTo(1);
        assertThat(s.getSinCambio()).isEqualTo(1);
        assertThat(s.getMensaje()).isEqualTo("Se actualizaron 1 precio(s) de EGSA.");
        assertThat(sube.getPrecioCosto()).isEqualByComparingTo("110.00");
        assertThat(sube.getPrecioVenta()).isEqualByComparingTo("121.00");

        ArgumentCaptor<ImportPrecioBatch> batch = ArgumentCaptor.forClass(ImportPrecioBatch.class);
        verify(batchRepo, atLeastOnce()).save(batch.capture());
        assertThat(batch.getValue().getProveedor()).isEqualTo(EGSA);
        assertThat(batch.getValue().getFuente()).isEqualTo("API_ENVIO");
        assertThat(batch.getValue().getArchivo()).isEqualTo("Lista recibida de EGSA");
        verify(retenidas).borrar(EGSA);
        assertThat(importService.iniciarImport()).as("candado libre").isTrue();
    }

    @Test
    void recibe_sinCambios() {
        producto("A1", "100.00", "BH");

        service.recibir(excel(fila("BH", "A1", "Arbol", 100)), null);

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.SIN_CAMBIOS);
        assertThat(ultima().getMensaje()).isEqualTo("La lista de EGSA no trae precios distintos a los que ya tenés.");
        verifyNoInteractions(historialRepo);
    }

    /**
     * El 106 tenia cargado el precio de la cadena RUL-REP en el arbol de levas BH: con la marca el
     * precio correcto aparece, pero como salta de golpe queda para que una persona lo apruebe.
     */
    @Test
    void recibe_codigosRepetidosConMarcasDistintas_cadaProductoTomaElPrecioDeSuMarca() {
        Producto arbol = producto("106", "18736.64", "BH");
        Producto goma = producto("107", "100.00", "ADON");

        service.recibir(excel(fila("BH", "106", "Arbol", 149104.95), fila("RUL-REP", "106", "Cadena", 18736.64),
                fila("ADON", "107", "Goma", 105), fila("STACO", "107", "Tensor", 9999)), null);

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(goma.getPrecioCosto()).isEqualByComparingTo("105.00");
        assertThat(arbol.getPrecioCosto()).as("espera la aprobacion").isEqualByComparingTo("18736.64");
        assertThat(s.getEnRevision()).isEqualTo(1);
        assertThat(s.getProblemas()).contains("2 código(s) vienen repetidos con marcas distintas");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrecioRevision>> revisiones = ArgumentCaptor.forClass(List.class);
        verify(revisionRepo).saveAll(revisiones.capture());
        assertThat(revisiones.getValue()).singleElement().satisfies(r -> {
            assertThat(r.getProducto()).isSameAs(arbol);
            assertThat(r.getCostoNuevo()).isEqualByComparingTo("149104.95");
        });
    }

    @Test
    void noEsUnExcel_devuelve400_sinCrearConstancia() {
        assertThatThrownBy(() -> service.recibir("hola".getBytes(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("no es un Excel");
                });
        assertThatThrownBy(() -> service.recibir(null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.recibir(new byte[]{'P', 'K'}, null)).isInstanceOf(ResponseStatusException.class);
        assertThat(guardadas).isEmpty();
    }

    @Test
    void conOtraActualizacionEnCurso_devuelve429_yNoLeSacaElCandado() {
        importService.iniciarImport();

        assertThatThrownBy(() -> service.recibir(excel(fila("BH", "A1", "x", 1)), null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(guardadas).isEmpty();
        assertThat(importService.iniciarImport()).as("sigue tomado por quien lo tenia").isFalse();
    }

    @Test
    void enSegundoPlano_devuelveLaConstanciaEnCurso_yElCandadoSigueTomadoHastaQueTermine() {
        List<Runnable> encoladas = new ArrayList<>();
        executor = encoladas::add;

        SincronizacionResponse r = service.recibir(excel(fila("BH", "A1", "x", 1)), "a.xlsx");

        assertThat(r.resultado()).isEqualTo("EN_CURSO");
        assertThat(encoladas).hasSize(1);
        assertThat(importService.iniciarImport()).isFalse();
    }

    @Test
    void siNoSePuedeEncolar_liberaElCandado() {
        executor = r -> { throw new RejectedExecutionException("lleno"); };

        assertThatThrownBy(() -> service.recibir(excel(fila("BH", "A1", "x", 1)), null))
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    // --- Listas raras ---

    @Test
    void listaRara_quedaRetenida_guardaElArchivo_ySinTocarPrecios() {
        Producto p = producto("A1", "100.00", "BH");
        byte[] lista = excel(fila("BH", "A1", "Arbol", 110), fila("BH", "N1", "Nuevo", 5),
                fila("BH", "N2", "Nuevo", 5), fila("BH", "N3", "Nuevo", 5));

        service.recibir(lista, "lista rara.xlsx");

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.RETENIDA);
        assertThat(s.getMensaje()).contains("lista de EGSA").contains("Aplicar igual");
        assertThat(s.getProblemas()).contains("Solo 1 de 4 códigos coinciden")
                .contains("3 código(s) de la lista son nuevos para EGSA")
                .contains("se dan de alta cuando la lista se aplique");
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100.00");
        ArgumentCaptor<byte[]> guardado = ArgumentCaptor.forClass(byte[].class);
        verify(retenidas).guardar(eq(EGSA), guardado.capture(), eq("lista rara.xlsx"));
        assertThat(guardado.getValue()).isEqualTo(lista);
        verify(retenidas, never()).borrar(anyString());
        verifyNoInteractions(bulkImporter, historialRepo);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void sinLaColumnaDePrecio_esErrorDeFormato() throws IOException {
        byte[] otra;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row h = wb.createSheet().createRow(0);
            h.createCell(0).setCellValue("Código");
            h.createCell(1).setCellValue("Precio Final");
            wb.write(out);
            otra = out.toByteArray();
        }

        service.recibir(otra, null);

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).startsWith("La lista de EGSA no tiene el formato de siempre: ")
                .contains("PrecioLista");
        verifyNoInteractions(retenidas);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void sinConfiguracionDeMargenes_loDiceTalCual() {
        when(configuracionRepo.findByProveedorIgnoreCase(EGSA)).thenReturn(Optional.empty());

        service.recibir(excel(fila("BH", "A1", "x", 1)), null);

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).isEqualTo("No hay configuración de margen para el proveedor: EGSA");
    }

    @Test
    void errorInesperado_noExponeElDetalle_yLiberaElCandado() {
        when(productoRepository.findBySkuIn(anyCollection())).thenThrow(new IllegalStateException("se cayo la base"));

        service.recibir(excel(fila("BH", "A1", "x", 1)), null);

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).contains("error interno").doesNotContain("se cayo la base");
        assertThat(importService.iniciarImport()).isTrue();
    }

    // --- Codigos nuevos ---

    @Test
    void codigoNuevo_seDaDeAltaYQuedaConPrecioEnLaMismaRecepcion() {
        producto("A1", "100.00", "BH");
        doAnswer(inv -> {
            List<ProductoImporter.FilaProducto> filas = inv.getArgument(0);
            filas.forEach(f -> producto(f.sku(), null, f.marca()));
            return null;
        }).when(bulkImporter).importar(anyList(), any(ImportJob.class));

        service.recibir(excel(fila("BH", "A1", "Arbol", 100), fila("RUL-REP", "N1", "Cadena nueva", 50)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> creadas = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(creadas.capture(), any(ImportJob.class));
        assertThat(creadas.getValue()).singleElement().satisfies(f -> {
            assertThat(f.sku()).isEqualTo("N1");
            assertThat(f.marca()).isEqualTo("RUL-REP");
            assertThat(f.descripcion()).isEqualTo("Cadena nueva");
            assertThat(f.proveedor()).isEqualTo(EGSA);
        });
        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(s.getActualizados()).isEqualTo(1);
        assertThat(s.getNoEncontrados()).isZero();
        assertThat(s.getProblemas()).isEqualTo("Se dieron de alta 1 producto(s) nuevo(s) de EGSA (ej: N1).");
        Producto nuevo = catalogo.stream().filter(p -> "N1".equals(p.getSku())).findFirst().orElseThrow();
        assertThat(nuevo.getPrecioCosto()).isEqualByComparingTo("50.00");
        assertThat(nuevo.getPrecioVenta()).isEqualByComparingTo("55.00");
    }

    @Test
    void codigoNuevoSinDescripcion_usaElCodigo() {
        producto("A1", "100.00", "BH");
        doAnswer(inv -> {
            List<ProductoImporter.FilaProducto> filas = inv.getArgument(0);
            filas.forEach(f -> producto(f.sku(), null, null));
            return null;
        }).when(bulkImporter).importar(anyList(), any(ImportJob.class));

        service.recibir(excel(fila("BH", "A1", "Arbol", 100), fila("", "N1", "", 50)), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoImporter.FilaProducto>> creadas = ArgumentCaptor.forClass(List.class);
        verify(bulkImporter).importar(creadas.capture(), any(ImportJob.class));
        assertThat(creadas.getValue().getFirst().descripcion()).isEqualTo("N1");
    }

    @Test
    void codigoNuevoQueNoSePudoCrear_quedaAnotado_yNoSeInventaUnPrecio() {
        producto("A1", "100.00", "BH");

        service.recibir(excel(fila("BH", "A1", "Arbol", 100), fila("BH", "N1", "Nuevo", 50)), null);

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.SIN_CAMBIOS);
        assertThat(s.getNoEncontrados()).isEqualTo(1);
        assertThat(s.getProblemas()).isEqualTo("1 código(s) nuevo(s) no se pudieron dar de alta.");
    }

    @Test
    void masCodigosNuevosQueElTope_noSeCreaNinguno() {
        for (int i = 0; i < 310; i++) producto("E" + i, "100.00", "BH");
        List<Object[]> filas = new ArrayList<>();
        for (int i = 0; i < 310; i++) filas.add(fila("BH", "E" + i, "x", 100));
        for (int i = 0; i < 301; i++) filas.add(fila("BH", "N" + i, "nuevo", 10));

        service.recibir(excel(filas.toArray(new Object[0][])), null);

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.SIN_CAMBIOS);
        assertThat(s.getNoEncontrados()).isEqualTo(301);
        assertThat(s.getProblemas()).contains("301 código(s) nuevos para EGSA").contains("son más de 300")
                .contains("no se dio de alta ninguno");
        verifyNoInteractions(bulkImporter);
    }

    // --- Resultado (lo que consulta el robot) ---

    @Test
    void resultado_deUnaRecepcion() {
        producto("A1", "100.00", "BH");
        service.recibir(excel(fila("BH", "A1", "Arbol", 110)), null);

        SincronizacionResponse r = service.resultado(ultima().getId());

        assertThat(r.resultado()).isEqualTo("ACTUALIZADA");
        assertThat(r.actualizados()).isEqualTo(1);
    }

    @Test
    void resultado_deAlgoQueNoEsUnaRecepcionDeEgsa_noExiste() {
        SincronizacionPrecio ads = new SincronizacionPrecio();
        ads.setId(500L);
        ads.setProveedor("Autopartes del Sur");
        ads.setOrigen(OrigenSincronizacion.RECEPCION);
        guardadas.put(500L, ads);
        SincronizacionPrecio manual = new SincronizacionPrecio();
        manual.setId(501L);
        manual.setProveedor(EGSA);
        manual.setOrigen(OrigenSincronizacion.AUTOMATICA);
        guardadas.put(501L, manual);

        assertThatThrownBy(() -> service.resultado(500L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resultado(501L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resultado(999L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No existe esa recepción");
    }

    // --- Aplicar igual a la lista retenida ---

    @Test
    void aplicarRetenida_usaElArchivoGuardado_yLoAplicaAunqueSeaRara() {
        Producto p = producto("A1", "100.00", "BH");
        byte[] lista = excel(fila("BH", "A1", "Arbol", 110), fila("BH", "N1", "Nuevo", 5),
                fila("BH", "N2", "Nuevo", 5), fila("BH", "N3", "Nuevo", 5));
        when(retenidas.leer(EGSA)).thenReturn(Optional.of(lista));
        doAnswer(inv -> {
            List<ProductoImporter.FilaProducto> filas = inv.getArgument(0);
            filas.forEach(f -> producto(f.sku(), null, null));
            return null;
        }).when(bulkImporter).importar(anyList(), any(ImportJob.class));

        SincronizacionResponse r = service.aplicarRetenida();

        assertThat(r.origen()).isEqualTo("MANUAL");
        assertThat(r.forzada()).isTrue();
        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(s.getMensaje()).endsWith("Se aplicó a pedido tuyo, aunque la lista llegó con datos raros.");
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("110.00");
        verify(retenidas).borrar(EGSA);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void aplicarRetenida_sinNingunaGuardada() {
        when(retenidas.leer(EGSA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarRetenida()).isInstanceOf(BusinessException.class)
                .hasMessage("No hay una lista de EGSA retenida para aplicar. El robot tiene que volver a mandarla.");
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void aplicarRetenida_conOtraEnCurso() {
        when(retenidas.leer(EGSA)).thenReturn(Optional.of(excel(fila("BH", "A1", "x", 1))));
        importService.iniciarImport();

        assertThatThrownBy(() -> service.aplicarRetenida()).isInstanceOf(BusinessException.class).hasMessageContaining("en curso");
    }

    @Test
    void aplicarRetenida_siNoSePuedeEncolar_liberaElCandado() {
        when(retenidas.leer(EGSA)).thenReturn(Optional.of(excel(fila("BH", "A1", "x", 1))));
        executor = r -> { throw new RejectedExecutionException("lleno"); };

        assertThatThrownBy(() -> service.aplicarRetenida()).isInstanceOf(RejectedExecutionException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void limpiarElNombreDelArchivo() {
        assertThat(RecepcionListaService.limpiar(null)).isNull();
        assertThat(RecepcionListaService.limpiar("  ")).isNull();
        assertThat(RecepcionListaService.limpiar("EGSA_ListaPrecios_05-10-2026.xlsx")).isEqualTo("EGSA_ListaPrecios_05-10-2026.xlsx");
        assertThat(RecepcionListaService.limpiar("a\nb\u0007<c>")).isEqualTo("a?b??c?");
        assertThat(RecepcionListaService.limpiar("x".repeat(300))).hasSize(120);
    }

    // --- Como lo manda Power Automate ---

    @Test
    void recibe_elArchivoEnBase64DentroDelObjetoDePowerAutomate() {
        Producto p = producto("A1", "100.00", "BH");
        String json = "{\"$content-type\":\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\","
                + "\"$content\":\"" + Base64.getEncoder().encodeToString(excel(fila("BH", "A1", "Arbol", 110))) + "\"}";

        service.recibir(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "lista.xlsx");

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("110.00");
    }

    @Test
    void recibe_elArchivoEnBase64Suelto_conSaltosDeLinea() {
        Producto p = producto("A1", "100.00", "BH");
        String base64 = Base64.getMimeEncoder().encodeToString(excel(fila("BH", "A1", "Arbol", 110)));

        service.recibir(("  " + base64 + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), null);

        assertThat(p.getPrecioCosto()).isEqualByComparingTo("110.00");
    }

    @Test
    void lasFormasQueNoSeEntienden_sonUn400DeArchivoQueNoEsExcel() {
        String[] cuerpos = {
                "{\"otra\":\"cosa\"}",                 // JSON sin $content
                "{\"$content\": 123}",                  // $content que no es texto
                "{esto no es json",                     // JSON roto
                "UEsD esto no es base64 !!!",           // empieza como base64 pero no lo es
                "{\"$content\":\"aGVsbG8gbXVuZG8=\"}"  // base64 valido de algo que no es un Excel
        };
        for (String c : cuerpos) {
            assertThatThrownBy(() -> service.recibir(c.getBytes(java.nio.charset.StandardCharsets.UTF_8), null))
                    .as(c).isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(guardadas).isEmpty();
    }

    @Test
    void desenvolver_elExcelCrudoQuedaIgual_yLoQueNoSeReconoceTambien() {
        byte[] excel = excel(fila("BH", "A1", "x", 1));
        assertThat(RecepcionListaService.desenvolver(excel)).isSameAs(excel);
        assertThat(RecepcionListaService.desenvolver(null)).isNull();
        byte[] corto = {1, 2};
        assertThat(RecepcionListaService.desenvolver(corto)).isSameAs(corto);
    }
}
