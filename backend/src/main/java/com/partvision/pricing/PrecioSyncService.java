package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.domain.HistorialPrecio;
import com.partvision.pricing.dto.ProveedorProducto;
import com.partvision.pricing.dto.SyncResultResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecioSyncService {

    private static final int PAGE_SIZE = 500;

    private final ProductoRepository productoRepository;
    private final ProveedorApiClient apiClient;
    private final ProveedorProperties props;
    private final ConfiguracionPrecioRepository configuracionRepo;
    private final PrecioImportService precioImportService;

    private final AtomicBoolean sincronizando = new AtomicBoolean(false);
    private final AtomicReference<SyncResultResponse> ultimoResultado = new AtomicReference<>();

    public boolean isSincronizando() {
        return sincronizando.get();
    }

    public SyncResultResponse getUltimoResultado() {
        return ultimoResultado.get();
    }

    public boolean iniciarSync() {
        return sincronizando.compareAndSet(false, true);
    }

    @Async("importExecutor")
    @Transactional
    public void ejecutarSyncAsync() {
        try {
            SyncResultResponse resultado = ejecutarSync();
            ultimoResultado.set(resultado);
        } catch (Exception e) {
            log.error("Error fatal en sync async", e);
            ultimoResultado.set(new SyncResultResponse(0, 0, 0, 1,
                    "Error durante la sincronización: " + e.getMessage()));
        } finally {
            sincronizando.set(false);
        }
    }

    private SyncResultResponse ejecutarSync() {
        List<Producto> productosConSku = productoRepository.findBySkuIsNotNull();
        if (productosConSku.isEmpty()) {
            return new SyncResultResponse(0, 0, 0, 0,
                    "No hay productos con SKU para sincronizar");
        }

        Map<String, Producto> productosPorSku = productosConSku.stream()
                .filter(p -> p.getSku() != null)
                .collect(Collectors.toMap(
                        p -> p.getSku().toUpperCase(),
                        Function.identity(),
                        (a, b) -> a
                ));

        log.info("Sync precios: {} SKUs en DB, paginando catálogo ADS (pageSize={})",
                productosPorSku.size(), PAGE_SIZE);

        String token = apiClient.login();
        BigDecimal margenDb = configuracionRepo.findByProveedorIgnoreCase("Autopartes del Sur")
                .map(ConfiguracionPrecio::getMargen)
                .orElse(props.margen());
        BigDecimal multiplicador = BigDecimal.ONE.add(margenDb.divide(BigDecimal.valueOf(100)));

        int actualizados = 0;
        int paginasProcesadas = 0;
        List<Producto> modificados = new ArrayList<>();
        List<HistorialPrecio> historiales = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        int page = 0;
        boolean hayMas = true;
        while (hayMas) {
            List<ProveedorProducto> pagina;
            try {
                pagina = apiClient.listarPagina(token, page, PAGE_SIZE);
            } catch (Exception e) {
                log.error("Error leyendo página {} del catálogo ADS: {}", page, e.getMessage());
                break;
            }

            if (pagina == null || pagina.isEmpty()) {
                hayMas = false;
                continue;
            }

            for (ProveedorProducto pp : pagina) {
                if (pp.codigo() == null) continue;
                Producto producto = productosPorSku.get(pp.codigo().toUpperCase());
                if (producto == null) continue;

                BigDecimal costo = pp.precioCostoSinIva();
                BigDecimal lista = pp.precioLista();
                if (costo == null) continue;
                BigDecimal venta = costo.multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);

                HistorialPrecio h = new HistorialPrecio();
                h.setProducto(producto);
                h.setPrecioCostoAnterior(producto.getPrecioCosto());
                h.setPrecioVentaAnterior(producto.getPrecioVenta());
                h.setPrecioCostoNuevo(costo);
                h.setPrecioVentaNuevo(venta);
                h.setMargenAplicado(margenDb);
                historiales.add(h);

                producto.setPrecioCosto(costo);
                producto.setPrecioLista(lista);
                producto.setPrecioVenta(venta);
                producto.setPrecioActualizadoEn(ahora);
                modificados.add(producto);
                actualizados++;
            }

            paginasProcesadas++;
            if (pagina.size() < PAGE_SIZE) {
                hayMas = false;
            } else {
                page++;
            }

            if (paginasProcesadas % 20 == 0) {
                log.info("Sync progreso: {} páginas, {} actualizados hasta ahora", paginasProcesadas, actualizados);
            }
        }

        if (!modificados.isEmpty()) {
            productoRepository.saveAll(modificados);
            var batch = precioImportService.crearBatchApiSync("Autopartes del Sur",
                    productosPorSku.size(), actualizados);
            historiales.forEach(h -> h.setBatch(batch));
            precioImportService.guardarHistorial(historiales);
        }

        int noEncontrados = productosPorSku.size() - actualizados;
        String mensaje = String.format(
                "Sincronización completada: %d páginas, %d actualizados, %d sin match (margen %.2f%%)",
                paginasProcesadas, actualizados, noEncontrados, margenDb);

        log.info(mensaje);
        return new SyncResultResponse(productosPorSku.size(), actualizados, noEncontrados, 0, mensaje);
    }
}
