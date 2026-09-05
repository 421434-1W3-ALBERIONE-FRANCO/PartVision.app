package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.*;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.domain.Stock;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.repository.StockRepository;
import com.partvision.inventory.service.StockService;
import com.partvision.location.domain.Ubicacion;
import com.partvision.location.service.UbicacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompraService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CompraRepository compraRepo;
    private final ProductoRepository productoRepo;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final UbicacionService ubicacionService;

    @Transactional
    public CompraResponse registrarRecepcion(RecepcionCompraRequest request) {
        Optional<Compra> existente = compraRepo.findByNumeroFactura(request.factura());
        if (existente.isPresent()) {
            log.info("Factura {} ya registrada, retornando existente", request.factura());
            return CompraResponse.from(existente.get(), true);
        }

        LocalDate fecha = parseFecha(request.fechaFactura());
        CompraEstado estado = parseEstado(request.estatus());

        Compra compra = new Compra();
        compra.setNumeroFactura(request.factura());
        compra.setFechaFactura(fecha);
        compra.setProveedor(request.proveedor());
        compra.setEstado(estado);

        Set<String> codigos = request.lineas().stream()
                .map(l -> codigoODefault(l.codigo()).toUpperCase())
                .collect(Collectors.toSet());

        Map<String, Producto> productosPorSku = productoRepo.findBySkuIn(codigos).stream()
                .collect(Collectors.toMap(
                        p -> p.getSku().toUpperCase(),
                        Function.identity(),
                        (a, b) -> a
                ));

        for (RecepcionLineaRequest lineaReq : request.lineas()) {
            String codigo = codigoODefault(lineaReq.codigo());
            CompraLinea linea = new CompraLinea();
            linea.setCodigo(codigo);
            linea.setDescripcion(lineaReq.descripcion());
            linea.setCantidad(lineaReq.cantidad());

            Producto producto = productosPorSku.get(codigo.toUpperCase());
            if (producto != null) {
                linea.setProducto(producto);
            }

            compra.addLinea(linea);
        }

        compra = compraRepo.save(compra);

        int matcheadas = (int) compra.getLineas().stream().filter(l -> l.getProducto() != null).count();
        log.info("Factura {} registrada: {} líneas, {} matcheadas, estado {}",
                compra.getNumeroFactura(), compra.getLineas().size(), matcheadas, estado);

        return CompraResponse.from(compra, true);
    }

    @Transactional
    public CompraResponse marcarIngresada(Long compraId, CambiarEstadoRequest request) {
        Compra compra = compraRepo.findWithLineasById(compraId)
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        if (compra.getEstado() == CompraEstado.INGRESADA) {
            throw new BusinessException("La compra ya fue marcada como ingresada");
        }

        Map<Long, Long> ubicacionPorLinea = request.asignaciones().stream()
                .collect(Collectors.toMap(
                        CambiarEstadoRequest.LineaUbicacion::lineaId,
                        CambiarEstadoRequest.LineaUbicacion::ubicacionId
                ));

        Set<Long> ubicacionIds = new HashSet<>(ubicacionPorLinea.values());
        Map<Long, Ubicacion> ubicacionesCache = ubicacionIds.stream()
                .collect(Collectors.toMap(Function.identity(), ubicacionService::getEntity));

        compra.setEstado(CompraEstado.INGRESADA);

        int cargados = 0;
        for (CompraLinea linea : compra.getLineas()) {
            Long ubicacionId = ubicacionPorLinea.get(linea.getId());
            if (ubicacionId == null) continue;

            Ubicacion ubicacion = ubicacionesCache.get(ubicacionId);
            linea.setUbicacionIngreso(ubicacion);

            if (linea.getProducto() == null) continue;

            stockService.registrarEntrada(new EntradaRequest(
                    linea.getProducto().getId(),
                    ubicacionId,
                    linea.getCantidad(),
                    "Compra factura #" + compra.getNumeroFactura()
            ));
            cargados++;
        }

        compraRepo.save(compra);
        log.info("Compra {} marcada INGRESADA: {} líneas con stock cargado (por ubicación individual)",
                compra.getNumeroFactura(), cargados);

        return CompraResponse.from(compra, true);
    }

    @Transactional(readOnly = true)
    public Page<CompraResponse> listar(CompraEstado estado, Pageable pageable) {
        Page<Compra> page = estado != null
                ? compraRepo.findByEstadoOrderByCreatedAtDesc(estado, pageable)
                : compraRepo.findAllByOrderByCreatedAtDesc(pageable);

        return page.map(c -> CompraResponse.from(c, false));
    }

    @Transactional(readOnly = true)
    public CompraResponse detalle(Long id) {
        Compra compra = compraRepo.findWithLineasById(id)
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        Map<Long, CompraResponse.UbicacionSugerida> stockSugerido = calcularSugerencias(compra);
        return CompraResponse.from(compra, true, stockSugerido);
    }

    private Map<Long, CompraResponse.UbicacionSugerida> calcularSugerencias(Compra compra) {
        List<Long> productoIds = compra.getLineas().stream()
                .filter(l -> l.getProducto() != null)
                .map(l -> l.getProducto().getId())
                .distinct()
                .toList();

        if (productoIds.isEmpty()) return Map.of();

        Map<Long, List<Stock>> stockPorProducto = stockRepository.findByProductoIdIn(productoIds)
                .stream()
                .filter(s -> s.getCantidad() > 0)
                .collect(Collectors.groupingBy(s -> s.getProducto().getId()));

        Map<Long, CompraResponse.UbicacionSugerida> result = new HashMap<>();
        for (var entry : stockPorProducto.entrySet()) {
            entry.getValue().stream()
                    .max(Comparator.comparingInt(Stock::getCantidad))
                    .ifPresent(best -> result.put(entry.getKey(),
                            new CompraResponse.UbicacionSugerida(
                                    best.getUbicacion().getId(),
                                    best.getUbicacion().getCodigo())));
        }
        return result;
    }

    private static String codigoODefault(String codigo) {
        return (codigo == null || codigo.isBlank()) ? "IMPORTADOS" : codigo.trim();
    }

    private LocalDate parseFecha(String fecha) {
        try {
            return LocalDate.parse(fecha, FMT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(fecha);
            } catch (DateTimeParseException e2) {
                throw new BusinessException("Formato de fecha inválido: " + fecha + ". Usar dd/MM/yyyy");
            }
        }
    }

    private CompraEstado parseEstado(String estatus) {
        if (estatus == null) return CompraEstado.EN_TRANSITO;
        String normalizado = estatus.toUpperCase().trim();
        if (normalizado.contains("INGRESADA")) return CompraEstado.INGRESADA;
        return CompraEstado.EN_TRANSITO;
    }
}
