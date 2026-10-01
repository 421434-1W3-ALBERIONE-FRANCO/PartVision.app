package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.imports.service.ProductoBulkImporter;
import com.partvision.pricing.AdsPortalClient.AdsPortalException;
import com.partvision.pricing.domain.*;
import com.partvision.pricing.dto.AlertaPreciosResponse;
import com.partvision.pricing.dto.RevisionPreciosResponse;
import com.partvision.pricing.dto.SincronizacionEstadoResponse;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SincronizacionPreciosServiceTest {

    private static final String ADS = "Autopartes del Sur";

    @Mock private AdsPortalClient portal;
    @Mock private ProductoRepository productoRepository;
    @Mock private ConfiguracionPrecioRepository configuracionRepo;
    @Mock private ImportPrecioBatchRepository batchRepo;
    @Mock private HistorialPrecioRepository historialRepo;
    @Mock private SincronizacionPrecioRepository syncRepo;
    @Mock private PrecioRevisionRepository revisionRepo;

    private PrecioImportService importService;
    private SincronizacionPreciosService service;
    private final Map<Long, SincronizacionPrecio> guardadas = new LinkedHashMap<>();
    private final List<Producto> catalogo = new ArrayList<>();
    private Executor executor = Runnable::run;

    @BeforeEach
    void setUp() {
        importService = new PrecioImportService(productoRepository, configuracionRepo, batchRepo, historialRepo,
                mock(ProductoBulkImporter.class));
        service = nuevoServicio(props("20111111112", "secreta"));

        ConfiguracionPrecio config = new ConfiguracionPrecio();
        config.setProveedor(ADS);
        config.setMargen(new BigDecimal("10"));
        config.setAjusteLista(BigDecimal.ZERO);
        when(configuracionRepo.findByProveedorIgnoreCase(ADS)).thenReturn(Optional.of(config));

        when(syncRepo.save(any())).thenAnswer(inv -> {
            SincronizacionPrecio s = inv.getArgument(0);
            if (s.getId() == null) s.setId((long) guardadas.size() + 1);
            guardadas.put(s.getId(), s);
            return s;
        });
        when(syncRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(guardadas.get(inv.<Long>getArgument(0))));
        when(syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(eq(ADS), any())).thenReturn(Optional.empty());
        when(batchRepo.save(any())).thenAnswer(inv -> {
            ImportPrecioBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(99L);
            return b;
        });
        when(productoRepository.findBySkuIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> skus = inv.getArgument(0);
            return catalogo.stream().filter(p -> skus.contains(p.getSku())).toList();
        });
    }

    private SincronizacionPreciosService nuevoServicio(AdsSyncProperties props) {
        return new SincronizacionPreciosService(props, portal, importService, productoRepository, batchRepo,
                historialRepo, syncRepo, revisionRepo, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                r -> executor.execute(r));
    }

    /** Umbrales chicos: con pocas filas de prueba no tiene que saltar el freno de "pocas filas". */
    private static AdsSyncProperties props(String usuario, String password) {
        return new AdsSyncProperties(usuario, password, "http://ads.test/", ADS, "Código", "Precio de Lista",
                60, 35, 10, 80, 1, 5, 50, 3, 5, 5, 1000);
    }

    private Producto producto(long id, String sku, String costo) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setDescripcion("Repuesto " + sku);
        p.setProveedor(ADS);
        p.setPrecioCosto(new BigDecimal(costo));
        p.setPrecioVenta(new BigDecimal(costo).multiply(new BigDecimal("1.1")).setScale(2));
        catalogo.add(p);
        return p;
    }

    /** Como la de ADS: titulo arriba, encabezado en la segunda fila. */
    private static byte[] excel(Object[]... filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Catalogo");
            sh.createRow(0).createCell(0).setCellValue("Autopartes Del Sur");
            Row h = sh.createRow(1);
            h.createCell(0).setCellValue("Código");
            h.createCell(1).setCellValue("Descripción");
            h.createCell(2).setCellValue("Precio de Lista");
            for (int i = 0; i < filas.length; i++) {
                Row r = sh.createRow(i + 2);
                r.createCell(0).setCellValue((String) filas[i][0]);
                r.createCell(1).setCellValue("desc");
                if (filas[i][1] instanceof Number n) r.createCell(2).setCellValue(n.doubleValue());
                else r.createCell(2).setCellValue((String) filas[i][1]);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private SincronizacionPrecio ultima() {
        return guardadas.values().stream().reduce((a, b) -> b).orElseThrow();
    }

    private SincronizacionPrecio sincronizacion(ResultadoSincronizacion r, LocalDateTime cuando, String mensaje) {
        SincronizacionPrecio s = new SincronizacionPrecio();
        s.setId(50L);
        s.setProveedor(ADS);
        s.setOrigen(OrigenSincronizacion.AUTOMATICA);
        s.setResultado(r);
        s.setIniciadaEn(cuando);
        s.setMensaje(mensaje);
        return s;
    }

    // --- La corrida ---

    @Test
    void programada_aplicaLoQueCambio_dejaParaRevisarLosSaltos_yLiberaElCandado() throws Exception {
        Producto sube = producto(1, "A1", "100.00");
        producto(2, "A2", "200.00");
        Producto salta = producto(3, "A3", "100.00");
        when(portal.descargarListaDelCliente()).thenReturn(excel(
                new Object[]{"A1", 110}, new Object[]{"A2", 200}, new Object[]{"A3", 500}));
        when(portal.descargarListaPublica()).thenReturn(excel(
                new Object[]{"A1", 130}, new Object[]{"A2", 200}, new Object[]{"A3", 500}));

        service.programada();

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(s.getOrigen()).isEqualTo(OrigenSincronizacion.AUTOMATICA);
        assertThat(s.getActualizados()).isEqualTo(1);
        assertThat(s.getSinCambio()).isEqualTo(1);
        assertThat(s.getEnRevision()).isEqualTo(1);
        assertThat(s.getConPrecioPropio()).isEqualTo(1);
        assertThat(s.getBatchId()).isEqualTo(99L);
        assertThat(s.getTerminadaEn()).isNotNull();
        assertThat(s.getMensaje()).isEqualTo("Se actualizaron 1 precio(s) de Autopartes del Sur. 1 esperan tu revisión.");

        assertThat(sube.getPrecioCosto()).isEqualByComparingTo("110.00");
        assertThat(sube.getPrecioVenta()).isEqualByComparingTo("121.00");
        assertThat(sube.getPrecioActualizadoEn()).isNotNull();
        assertThat(salta.getPrecioCosto()).isEqualByComparingTo("100.00");

        ArgumentCaptor<ImportPrecioBatch> batch = ArgumentCaptor.forClass(ImportPrecioBatch.class);
        verify(batchRepo, times(2)).save(batch.capture());
        assertThat(batch.getValue().getFuente()).isEqualTo("API_SYNC");
        assertThat(batch.getValue().getArchivo()).isEqualTo("Actualización automática de ADS");
        assertThat(batch.getValue().getAplicados()).isEqualTo(1);
        assertThat(batch.getValue().getTotal()).isEqualTo(3);

        @SuppressWarnings("unchecked") ArgumentCaptor<List<HistorialPrecio>> historial = ArgumentCaptor.forClass(List.class);
        verify(historialRepo).saveAll(historial.capture());
        assertThat(historial.getValue()).singleElement().satisfies(h -> {
            assertThat(h.getPrecioCostoAnterior()).isEqualByComparingTo("100.00");
            assertThat(h.getPrecioCostoNuevo()).isEqualByComparingTo("110.00");
        });

        verify(revisionRepo).vencerPendientes(eq(EstadoRevisionPrecio.PENDIENTE), eq(EstadoRevisionPrecio.VENCIDA),
                eq(ADS), any());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<PrecioRevision>> revisiones = ArgumentCaptor.forClass(List.class);
        verify(revisionRepo).saveAll(revisiones.capture());
        assertThat(revisiones.getValue()).singleElement().satisfies(r -> {
            assertThat(r.getProducto()).isSameAs(salta);
            assertThat(r.getCostoActual()).isEqualByComparingTo("100.00");
            assertThat(r.getCostoNuevo()).isEqualByComparingTo("500.00");
            assertThat(r.getPrecioLista()).isEqualByComparingTo("500");
            assertThat(r.getVariacionPct()).isEqualByComparingTo("400.00");
            assertThat(r.getSincronizacionId()).isEqualTo(s.getId());
        });

        assertThat(importService.iniciarImport()).as("el candado quedo libre").isTrue();
    }

    @Test
    void sinCambios_noCreaBatchNiHistorial() throws Exception {
        producto(1, "A1", "100.00");
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 100}));
        when(portal.descargarListaPublica()).thenReturn(excel(new Object[]{"A1", 120}));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.SIN_CAMBIOS);
        assertThat(ultima().getMensaje()).isEqualTo("La lista de ADS no trae precios distintos a los que ya tenés.");
        assertThat(ultima().getBatchId()).isNull();
        verifyNoInteractions(batchRepo, historialRepo);
        verify(revisionRepo, never()).saveAll(any());
    }

    @Test
    void listaIgualALaPublica_quedaRetenida_sinTocarNada() throws Exception {
        Producto p = producto(1, "A1", "100.00");
        byte[] lista = excel(new Object[]{"A1", 110});
        when(portal.descargarListaDelCliente()).thenReturn(lista);
        when(portal.descargarListaPublica()).thenReturn(lista);

        service.programada();

        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.RETENIDA);
        assertThat(s.getMensaje()).contains("Aplicar igual");
        assertThat(s.getProblemas()).contains("sin los precios especiales de tu cuenta");
        assertThat(s.getConPrecioPropio()).isZero();
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100.00");
        verifyNoInteractions(batchRepo, historialRepo);
        verify(revisionRepo, never()).vencerPendientes(any(), any(), any(), any());
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void usaLaUltimaCorridaBuenaComoReferencia() throws Exception {
        producto(1, "A1", "100.00");
        SincronizacionPrecio anterior = sincronizacion(ResultadoSincronizacion.ACTUALIZADA, LocalDateTime.now().minusDays(1), "ok");
        anterior.setFilasLista(67_280);
        when(syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(eq(ADS), any())).thenReturn(Optional.of(anterior));
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 110}));
        when(portal.descargarListaPublica()).thenReturn(excel(new Object[]{"A1", 120}));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.RETENIDA);
        assertThat(ultima().getProblemas()).contains("la última traía 67.280");
    }

    @Test
    void forzada_aplicaAunqueLaListaHayaLlegadoRara() throws Exception {
        Producto p = producto(1, "A1", "100.00");
        byte[] lista = excel(new Object[]{"A1", 110});
        when(portal.descargarListaDelCliente()).thenReturn(lista);
        when(portal.descargarListaPublica()).thenReturn(lista);

        SincronizacionResponse r = service.iniciarManual(true);

        assertThat(r.origen()).isEqualTo("MANUAL");
        assertThat(r.forzada()).isTrue();
        SincronizacionPrecio s = ultima();
        assertThat(s.getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(s.getMensaje()).endsWith("Se aplicó a pedido tuyo, aunque la lista llegó con datos raros.");
        assertThat(s.getProblemas()).contains("sin los precios especiales");
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("110.00");
        ArgumentCaptor<ImportPrecioBatch> batch = ArgumentCaptor.forClass(ImportPrecioBatch.class);
        verify(batchRepo, atLeastOnce()).save(batch.capture());
        assertThat(batch.getValue().getArchivo()).isEqualTo("Actualización de ADS (botón)");
    }

    @Test
    void sinListaPublica_aplicaIgual_yLoAnota() throws Exception {
        producto(1, "A1", "100.00");
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 110}));
        when(portal.descargarListaPublica()).thenThrow(new AdsPortalException("caido"));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(ultima().getConPrecioPropio()).isNull();
        assertThat(ultima().getProblemas()).contains("No se pudo bajar la lista pública");
    }

    @Test
    void listaPublicaIlegible_tambienSeToleraYSeIgnoranSusFilasMalas() throws Exception {
        producto(1, "A1", "100.00");
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 110}));
        when(portal.descargarListaPublica()).thenReturn(excel(new Object[]{"A1", "sin precio"}));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.RETENIDA);
        assertThat(ultima().getConPrecioPropio()).isZero();

        when(portal.descargarListaPublica()).thenReturn("no es un excel".getBytes());
        service.programada();
        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ACTUALIZADA);
        assertThat(ultima().getConPrecioPropio()).isNull();
    }

    @Test
    void errorDelPortal_quedaConstanciaConSuMensaje() throws Exception {
        when(portal.descargarListaDelCliente()).thenThrow(new AdsPortalException("ADS rechazó el usuario o la contraseña."));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).isEqualTo("ADS rechazó el usuario o la contraseña.");
        assertThat(ultima().getTerminadaEn()).isNotNull();
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void listaSinLaColumnaDePrecio_esErrorDeFormato() throws Exception {
        byte[] otra;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row h = wb.createSheet().createRow(0);
            h.createCell(0).setCellValue("Código");
            h.createCell(1).setCellValue("Precio Final");
            wb.write(out);
            otra = out.toByteArray();
        }
        when(portal.descargarListaDelCliente()).thenReturn(otra);

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).startsWith("La lista de ADS no tiene el formato de siempre: ")
                .contains("Precio de Lista");
        verify(portal, never()).descargarListaPublica();
    }

    @Test
    void sinConfiguracionDeMargenes_loDiceTalCual() throws Exception {
        when(configuracionRepo.findByProveedorIgnoreCase(ADS)).thenReturn(Optional.empty());
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 110}));
        when(portal.descargarListaPublica()).thenReturn(excel(new Object[]{"A1", 120}));

        service.programada();

        assertThat(ultima().getMensaje()).isEqualTo("No hay configuración de margen para el proveedor: Autopartes del Sur");
    }

    @Test
    void errorInesperado_noExponeElDetalle_yLiberaElCandado() throws Exception {
        when(portal.descargarListaDelCliente()).thenReturn(excel(new Object[]{"A1", 110}));
        when(portal.descargarListaPublica()).thenReturn(excel(new Object[]{"A1", 120}));
        when(productoRepository.findBySkuIn(anyCollection())).thenThrow(new IllegalStateException("se cayo la base"));

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).contains("error interno").doesNotContain("se cayo la base");
        assertThat(importService.iniciarImport()).isTrue();
    }

    // --- Cuando no puede correr ---

    @Test
    void programada_sinCredenciales_noHaceNada() {
        nuevoServicio(props("", "")).programada();

        verifyNoInteractions(portal, syncRepo);
    }

    @Test
    void programada_conUnaImportacionEnCurso_dejaConstanciaYNoLaInterrumpe() {
        importService.iniciarImport();

        service.programada();

        assertThat(ultima().getResultado()).isEqualTo(ResultadoSincronizacion.ERROR);
        assertThat(ultima().getMensaje()).contains("había una importación de precios en curso");
        verifyNoInteractions(portal);
        assertThat(importService.iniciarImport()).as("la importacion manual sigue con su candado").isFalse();
    }

    @Test
    void programada_siNoPuedeRegistrarLaCorrida_liberaElCandado() {
        doThrow(new IllegalStateException("base caida")).when(syncRepo).save(any());

        assertThatThrownBy(service::programada).isInstanceOf(IllegalStateException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void manual_sinCredenciales() {
        assertThatThrownBy(() -> nuevoServicio(props(null, null)).iniciarManual(false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no está configurada");
    }

    @Test
    void manual_conOtraEnCurso() {
        importService.iniciarImport();

        assertThatThrownBy(() -> service.iniciarManual(false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en curso");
    }

    @Test
    void manual_corriendoEnSegundoPlano_devuelveLaCorridaEnCurso() {
        List<Runnable> encoladas = new ArrayList<>();
        executor = encoladas::add;

        SincronizacionResponse r = service.iniciarManual(false);

        assertThat(r.resultado()).isEqualTo("EN_CURSO");
        assertThat(encoladas).hasSize(1);
        assertThat(importService.iniciarImport()).as("el candado sigue tomado hasta que termine").isFalse();
    }

    @Test
    void manual_siNoSePuedeEncolar_liberaElCandado() {
        executor = r -> { throw new RejectedExecutionException("lleno"); };

        assertThatThrownBy(() -> service.iniciarManual(false)).isInstanceOf(RejectedExecutionException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void alArrancar_cierraLasQueQuedaronCortadas() {
        when(syncRepo.cerrarInterrumpidas(any(), any(), any(), any())).thenReturn(2, 0);

        service.cerrarInterrumpidas();
        service.cerrarInterrumpidas();

        verify(syncRepo, times(2)).cerrarInterrumpidas(eq(ResultadoSincronizacion.EN_CURSO),
                eq(ResultadoSincronizacion.ERROR), any(), contains("reinició"));
    }

    // --- Revision ---

    private PrecioRevision revision(long id, Producto p, String lista, EstadoRevisionPrecio estado) {
        PrecioRevision r = new PrecioRevision();
        r.setId(id);
        r.setProducto(p);
        r.setPrecioLista(new BigDecimal(lista));
        r.setCostoActual(p.getPrecioCosto());
        r.setCostoNuevo(new BigDecimal(lista));
        r.setVariacionPct(new BigDecimal("400"));
        r.setEstado(estado);
        return r;
    }

    @Test
    void aplicarRevisiones_conLaTarifaDeHoy_yConHistorialParaRevertir() {
        Producto p = producto(1, "A1", "100.00");
        Producto yaResuelto = producto(2, "A2", "100.00");
        PrecioRevision r = revision(10, p, "500", EstadoRevisionPrecio.PENDIENTE);
        when(revisionRepo.findConProductoByIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(r, revision(11, yaResuelto, "900", EstadoRevisionPrecio.DESCARTADA)));

        RevisionPreciosResponse resp = service.aplicarRevisiones(List.of(10L, 11L));

        assertThat(resp.resueltas()).isEqualTo(1);
        assertThat(resp.batchId()).isEqualTo(99L);
        assertThat(resp.mensaje()).contains("Se aplicaron 1 precio(s)");
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("500.00");
        assertThat(p.getPrecioVenta()).isEqualByComparingTo("550.00");
        assertThat(yaResuelto.getPrecioCosto()).isEqualByComparingTo("100.00");
        assertThat(r.getEstado()).isEqualTo(EstadoRevisionPrecio.APLICADA);
        assertThat(r.getResueltaEn()).isNotNull();
        verify(historialRepo).saveAll(argThat(l -> ((List<?>) l).size() == 1));
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void aplicarRevisiones_sinPendientes() {
        when(revisionRepo.findConProductoByIdIn(List.of(10L))).thenReturn(List.of());

        assertThat(service.aplicarRevisiones(List.of(10L)).mensaje()).isEqualTo("No había precios pendientes para aplicar.");
        verifyNoInteractions(batchRepo);
    }

    @Test
    void revisiones_exigenElegirPrecios() {
        assertThatThrownBy(() -> service.aplicarRevisiones(null)).isInstanceOf(BusinessException.class)
                .hasMessage("Elegí al menos un precio.");
        assertThatThrownBy(() -> service.descartarRevisiones(List.of())).isInstanceOf(BusinessException.class);
        assertThat(importService.iniciarImport()).isTrue();
    }

    @Test
    void revisiones_noMientrasCorreUnaActualizacion() {
        importService.iniciarImport();

        assertThatThrownBy(() -> service.aplicarRevisiones(List.of(1L))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("en curso");
        verifyNoInteractions(revisionRepo);
    }

    @Test
    void descartarRevisiones() {
        Producto p = producto(1, "A1", "100.00");
        PrecioRevision r = revision(10, p, "500", EstadoRevisionPrecio.PENDIENTE);
        when(revisionRepo.findConProductoByIdIn(List.of(10L))).thenReturn(List.of(r));

        RevisionPreciosResponse resp = service.descartarRevisiones(List.of(10L));

        assertThat(resp.resueltas()).isEqualTo(1);
        assertThat(resp.mensaje()).contains("Se descartaron 1").contains("vuelven a aparecer");
        assertThat(r.getEstado()).isEqualTo(EstadoRevisionPrecio.DESCARTADA);
        assertThat(p.getPrecioCosto()).isEqualByComparingTo("100.00");

        when(revisionRepo.findConProductoByIdIn(List.of(10L))).thenReturn(List.of());
        assertThat(service.descartarRevisiones(List.of(10L)).mensaje()).startsWith("No había");
    }

    @Test
    void revisionesPendientes_conLosDatosDelProducto() {
        Producto p = producto(7, "A1", "100.00");
        when(revisionRepo.findPendientes(eq(EstadoRevisionPrecio.PENDIENTE), eq(ADS), any()))
                .thenReturn(List.of(revision(10, p, "500", EstadoRevisionPrecio.PENDIENTE)));

        assertThat(service.revisionesPendientes()).singleElement().satisfies(r -> {
            assertThat(r.productoId()).isEqualTo(7);
            assertThat(r.sku()).isEqualTo("A1");
            assertThat(r.descripcion()).isEqualTo("Repuesto A1");
            assertThat(r.costoNuevo()).isEqualByComparingTo("500");
        });
    }

    // --- Estado y avisos ---

    private void historial(SincronizacionPrecio... corridas) {
        when(syncRepo.findByProveedorOrderByIniciadaEnDesc(eq(ADS), any())).thenReturn(List.of(corridas));
    }

    private void ultimaBuena(SincronizacionPrecio s) {
        when(syncRepo.findFirstByProveedorAndResultadoInOrderByIniciadaEnDesc(eq(ADS), any())).thenReturn(Optional.ofNullable(s));
    }

    @Test
    void estado_todoBien() {
        SincronizacionPrecio ok = sincronizacion(ResultadoSincronizacion.ACTUALIZADA, LocalDateTime.now().minusHours(3), "ok");
        ok.setProblemas("uno\n\ndos");
        historial(ok);
        ultimaBuena(ok);

        SincronizacionEstadoResponse e = service.estado();

        assertThat(e.habilitada()).isTrue();
        assertThat(e.proveedor()).isEqualTo(ADS);
        assertThat(e.enCurso()).isFalse();
        assertThat(e.ultima().problemas()).containsExactly("uno", "dos");
        assertThat(e.ultimaBuenaEn()).isEqualTo(ok.getIniciadaEn());
        assertThat(e.alerta()).isNull();
        assertThat(e.historial()).hasSize(1);
    }

    @Test
    void estado_sinCorridas() {
        historial();

        SincronizacionEstadoResponse e = service.estado();

        assertThat(e.ultima()).isNull();
        assertThat(e.ultimaBuenaEn()).isNull();
        assertThat(e.alerta()).isNull();
        assertThat(service.alerta()).isEmpty();
    }

    @Test
    void alerta_apagadaSiNoEstaConfigurada() {
        historial(sincronizacion(ResultadoSincronizacion.ERROR, LocalDateTime.now(), "x"));

        assertThat(nuevoServicio(props("", "")).alerta()).isEmpty();
    }

    @Test
    void alerta_conUnaEnCurso_miraLaAnterior() {
        SincronizacionPrecio enCurso = sincronizacion(ResultadoSincronizacion.EN_CURSO, LocalDateTime.now(), null);
        SincronizacionPrecio error = sincronizacion(ResultadoSincronizacion.ERROR, LocalDateTime.now().minusHours(6), "ADS caido");
        historial(enCurso, error);
        ultimaBuena(sincronizacion(ResultadoSincronizacion.SIN_CAMBIOS, LocalDateTime.now().minusDays(1), "ok"));

        assertThat(service.estado().enCurso()).isTrue();
        assertThat(service.alerta()).get().satisfies(a -> {
            assertThat(a.nivel()).isEqualTo("ERROR");
            assertThat(a.mensaje()).isEqualTo("No se pudieron actualizar los precios de Autopartes del Sur: ADS caido");
        });

        historial(enCurso);
        assertThat(service.alerta()).isEmpty();
    }

    @Test
    void alerta_preciosViejos() {
        historial(sincronizacion(ResultadoSincronizacion.ERROR, LocalDateTime.now(), "ADS caido."));
        ultimaBuena(sincronizacion(ResultadoSincronizacion.ACTUALIZADA, LocalDateTime.of(2026, 9, 20, 7, 0), "ok"));

        AlertaPreciosResponse a = service.alerta().orElseThrow();

        assertThat(a.nivel()).isEqualTo("ERROR");
        assertThat(a.mensaje()).isEqualTo("Los precios de Autopartes del Sur no se actualizan desde el 20/09 07:00. ADS caido.");
    }

    @Test
    void alerta_nuncaHuboUnaBuena() {
        historial(sincronizacion(ResultadoSincronizacion.ERROR, LocalDateTime.now(), "Clave mal."));
        ultimaBuena(null);

        assertThat(service.alerta().orElseThrow().mensaje())
                .isEqualTo("No se pudieron actualizar los precios de Autopartes del Sur: Clave mal.");

        historial(sincronizacion(ResultadoSincronizacion.RETENIDA, LocalDateTime.now(), "x"));
        assertThat(service.alerta().orElseThrow().nivel()).isEqualTo("AVISO");
    }

    @Test
    void alerta_retenida() {
        historial(sincronizacion(ResultadoSincronizacion.RETENIDA, LocalDateTime.now(), "x"));
        ultimaBuena(sincronizacion(ResultadoSincronizacion.ACTUALIZADA, LocalDateTime.now().minusDays(1), "ok"));

        assertThat(service.alerta().orElseThrow()).satisfies(a -> {
            assertThat(a.nivel()).isEqualTo("AVISO");
            assertThat(a.mensaje()).contains("llegó con datos raros");
        });
    }

    @Test
    void alerta_preciosParaRevisar() {
        SincronizacionPrecio ok = sincronizacion(ResultadoSincronizacion.ACTUALIZADA, LocalDateTime.now(), "ok");
        historial(ok);
        ultimaBuena(ok);
        when(revisionRepo.contarPorEstado(EstadoRevisionPrecio.PENDIENTE, ADS)).thenReturn(1200L);

        assertThat(service.alerta().orElseThrow()).satisfies(a -> {
            assertThat(a.nivel()).isEqualTo("AVISO");
            assertThat(a.mensaje()).startsWith("1.200 precio(s) de Autopartes del Sur cambiaron más de lo normal");
        });
        assertThat(service.estado().pendientesRevision()).isEqualTo(1200);
    }

    @Test
    void resultado_sonBuenasSoloLasQueDejanLosPreciosAlDia() {
        assertThat(ResultadoSincronizacion.ACTUALIZADA.esBuena()).isTrue();
        assertThat(ResultadoSincronizacion.SIN_CAMBIOS.esBuena()).isTrue();
        assertThat(ResultadoSincronizacion.RETENIDA.esBuena()).isFalse();
        assertThat(ResultadoSincronizacion.ERROR.esBuena()).isFalse();
        assertThat(ResultadoSincronizacion.EN_CURSO.esBuena()).isFalse();
    }
}
