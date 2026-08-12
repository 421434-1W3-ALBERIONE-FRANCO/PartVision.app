package com.partvision.inventory.service;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.inventory.domain.MovimientoStock;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.inventory.dto.AjusteRequest;
import com.partvision.inventory.dto.ConteoLoteRequest;
import com.partvision.inventory.dto.ConteoRequest;
import com.partvision.inventory.dto.ConteoResponse;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.dto.SalidaRequest;
import com.partvision.inventory.dto.StockResumenResponse;
import com.partvision.inventory.dto.TransferenciaRequest;
import com.partvision.inventory.repository.MovimientoStockRepository;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final MovimientoStockRepository movimientoRepository;
    private final ProductoService productoService;
    private final UbicacionService ubicacionService;

    @Transactional
    public MovimientoResponse registrarEntrada(EntradaRequest request) {
        Producto producto = productoService.getEntity(request.productoId());
        Ubicacion ubicacion = ubicacionService.getEntity(request.ubicacionId());

        Stock stock = obtenerOCrear(producto, ubicacion);
        stock.setCantidad(stock.getCantidad() + request.cantidad());
        stockRepository.save(stock);

        return registrarMovimiento(TipoMovimiento.ENTRADA, producto, request.cantidad(),
                null, ubicacion, request.motivo());
    }

    @Transactional
    public MovimientoResponse registrarSalida(SalidaRequest request) {
        Producto producto = productoService.getEntity(request.productoId());
        Ubicacion ubicacion = ubicacionService.getEntity(request.ubicacionId());

        Stock stock = lockOExcepcion(producto, ubicacion);
        descontar(stock, request.cantidad());
        stockRepository.save(stock);

        return registrarMovimiento(TipoMovimiento.SALIDA, producto, request.cantidad(),
                ubicacion, null, request.motivo());
    }

    @Transactional
    public MovimientoResponse ajustar(AjusteRequest request) {
        Producto producto = productoService.getEntity(request.productoId());
        Ubicacion ubicacion = ubicacionService.getEntity(request.ubicacionId());

        Stock stock;
        switch (request.tipo()) {
            case AJUSTE_POSITIVO -> {
                stock = obtenerOCrear(producto, ubicacion);
                stock.setCantidad(stock.getCantidad() + request.cantidad());
            }
            case AJUSTE_NEGATIVO -> {
                stock = lockOExcepcion(producto, ubicacion);
                descontar(stock, request.cantidad());
            }
            default -> throw new BusinessException("Tipo de ajuste invalido: " + request.tipo());
        }
        stockRepository.save(stock);

        return registrarMovimiento(request.tipo(), producto, request.cantidad(),
                null, ubicacion, request.motivo());
    }

    /**
     * Fija la cantidad real de un producto en una ubicacion (conteo fisico) y genera
     * el ajuste por la diferencia. Si la cantidad ya coincidia, no crea movimiento.
     */
    @Transactional
    public ConteoResponse registrarConteo(ConteoRequest request) {
        Producto producto = productoService.getEntity(request.productoId());
        Ubicacion ubicacion = ubicacionService.getEntity(request.ubicacionId());

        Stock stock = obtenerOCrear(producto, ubicacion);
        int anterior = stock.getCantidad();
        int real = request.cantidadReal();
        int delta = real - anterior;

        stock.setCantidad(real);
        stockRepository.save(stock);

        MovimientoResponse movimiento = null;
        if (delta != 0) {
            TipoMovimiento tipo = delta > 0 ? TipoMovimiento.AJUSTE_POSITIVO : TipoMovimiento.AJUSTE_NEGATIVO;
            String motivo = request.motivo() == null || request.motivo().isBlank()
                    ? "Conteo fisico" : request.motivo();
            movimiento = registrarMovimiento(tipo, producto, Math.abs(delta), null, ubicacion, motivo);
        }
        return new ConteoResponse(producto.getId(), ubicacion.getId(), anterior, real, movimiento);
    }

    /** Conteo de varias lineas en una sola transaccion (todo o nada). */
    @Transactional
    public List<ConteoResponse> registrarConteoLote(ConteoLoteRequest request) {
        return request.conteos().stream().map(this::registrarConteo).toList();
    }

    @Transactional
    public MovimientoResponse transferir(TransferenciaRequest request) {
        if (request.ubicacionOrigenId().equals(request.ubicacionDestinoId())) {
            throw new BusinessException("El origen y el destino no pueden ser la misma ubicacion");
        }
        Producto producto = productoService.getEntity(request.productoId());
        Ubicacion origen = ubicacionService.getEntity(request.ubicacionOrigenId());
        Ubicacion destino = ubicacionService.getEntity(request.ubicacionDestinoId());

        Stock stockOrigen;
        Stock stockDestino;
        // Se adquieren los locks en orden ascendente de ubicacion para evitar deadlocks.
        if (origen.getId() < destino.getId()) {
            stockOrigen = lockOExcepcion(producto, origen);
            stockDestino = obtenerOCrear(producto, destino);
        } else {
            stockDestino = obtenerOCrear(producto, destino);
            stockOrigen = lockOExcepcion(producto, origen);
        }

        descontar(stockOrigen, request.cantidad());
        stockDestino.setCantidad(stockDestino.getCantidad() + request.cantidad());
        stockRepository.save(stockOrigen);
        stockRepository.save(stockDestino);

        return registrarMovimiento(TipoMovimiento.TRANSFERENCIA, producto, request.cantidad(),
                origen, destino, request.motivo());
    }

    /** Elimina la existencia de un producto en una ubicacion (borra la fila de stock). */
    @Transactional
    public void eliminarStock(Long productoId, Long ubicacionId) {
        Producto producto = productoService.getEntity(productoId);
        Ubicacion ubicacion = ubicacionService.getEntity(ubicacionId);
        Stock stock = lockOExcepcion(producto, ubicacion);
        stockRepository.delete(stock);
    }

    @Transactional(readOnly = true)
    public StockResumenResponse getResumen(Long productoId) {
        productoService.getEntity(productoId); // valida existencia -> 404
        return StockResumenResponse.from(productoId, stockRepository.findByProductoId(productoId));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MovimientoResponse> listarMovimientos(
            Long productoId, org.springframework.data.domain.Pageable pageable) {
        productoService.getEntity(productoId);
        return movimientoRepository.findByProductoIdOrderByCreatedAtDesc(productoId, pageable)
                .map(MovimientoResponse::from);
    }

    private Stock obtenerOCrear(Producto producto, Ubicacion ubicacion) {
        return stockRepository.lockByProductoAndUbicacion(producto.getId(), ubicacion.getId())
                .orElseGet(() -> stockRepository.save(
                        Stock.builder().producto(producto).ubicacion(ubicacion).cantidad(0).build()));
    }

    private Stock lockOExcepcion(Producto producto, Ubicacion ubicacion) {
        return stockRepository.lockByProductoAndUbicacion(producto.getId(), ubicacion.getId())
                .orElseThrow(() -> new BusinessException(
                        "No hay stock del producto en la ubicacion indicada"));
    }

    private void descontar(Stock stock, int cantidad) {
        if (stock.getCantidad() < cantidad) {
            throw new BusinessException("Stock insuficiente: disponible %d, solicitado %d"
                    .formatted(stock.getCantidad(), cantidad));
        }
        stock.setCantidad(stock.getCantidad() - cantidad);
    }

    private MovimientoResponse registrarMovimiento(TipoMovimiento tipo, Producto producto, int cantidad,
                                                   Ubicacion origen, Ubicacion destino, String motivo) {
        MovimientoStock movimiento = movimientoRepository.save(MovimientoStock.builder()
                .producto(producto)
                .tipo(tipo)
                .cantidad(cantidad)
                .ubicacionOrigen(origen)
                .ubicacionDestino(destino)
                .motivo(motivo)
                .build());
        return MovimientoResponse.from(movimiento);
    }
}
