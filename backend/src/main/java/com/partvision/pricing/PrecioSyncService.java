package com.partvision.pricing;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.pricing.domain.ConfiguracionPrecio;
import com.partvision.pricing.dto.ProveedorProducto;
import com.partvision.pricing.dto.SyncResultResponse;
import com.partvision.pricing.repository.ConfiguracionPrecioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrecioSyncService {

    private final ProductoRepository productoRepository;
    private final ProveedorApiClient apiClient;
    private final ProveedorProperties props;
    private final ConfiguracionPrecioRepository configuracionRepo;

    private final AtomicBoolean sincronizando = new AtomicBoolean(false);

    public boolean isSincronizando() {
        return sincronizando.get();
    }

    @Transactional
    public SyncResultResponse sincronizar() {
        if (!sincronizando.compareAndSet(false, true)) {
            return new SyncResultResponse(0, 0, 0, 0, "Ya hay una sincronización en curso");
        }

        try {
            return ejecutarSync();
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

        log.info("Iniciando sincronización de precios: {} productos con SKU", productosConSku.size());

        String token = apiClient.login();
        BigDecimal margenDb = configuracionRepo.findByProveedorIgnoreCase("Autopartes del Sur")
                .map(ConfiguracionPrecio::getMargen)
                .orElse(props.margen());
        BigDecimal multiplicador = BigDecimal.ONE.add(margenDb.divide(BigDecimal.valueOf(100)));

        int actualizados = 0;
        int noEncontrados = 0;
        int errores = 0;
        List<Producto> modificados = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (Producto producto : productosConSku) {
            try {
                Optional<ProveedorProducto> match = apiClient.buscarPorCodigo(token, producto.getSku());
                if (match.isPresent()) {
                    ProveedorProducto pp = match.get();
                    BigDecimal costo = pp.precioCostoSinIva();
                    BigDecimal lista = pp.precioLista();
                    BigDecimal venta = costo.multiply(multiplicador).setScale(2, RoundingMode.HALF_UP);

                    producto.setPrecioCosto(costo);
                    producto.setPrecioLista(lista);
                    producto.setPrecioVenta(venta);
                    producto.setPrecioActualizadoEn(ahora);
                    modificados.add(producto);
                    actualizados++;
                } else {
                    noEncontrados++;
                }
            } catch (Exception e) {
                log.warn("Error sincronizando SKU {}: {}", producto.getSku(), e.getMessage());
                errores++;
            }
        }

        if (!modificados.isEmpty()) {
            productoRepository.saveAll(modificados);
        }

        String mensaje = String.format(
                "Sincronización completada: %d actualizados, %d no encontrados, %d errores (margen %.2f%%)",
                actualizados, noEncontrados, errores, margenDb);

        log.info(mensaje);
        return new SyncResultResponse(productosConSku.size(), actualizados, noEncontrados, errores, mensaje);
    }
}
