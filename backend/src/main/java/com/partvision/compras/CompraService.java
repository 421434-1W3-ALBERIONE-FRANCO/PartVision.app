package com.partvision.compras;

import com.partvision.catalog.domain.Producto;
import com.partvision.catalog.repository.ProductoRepository;
import com.partvision.common.exception.BusinessException;
import com.partvision.compras.domain.Compra;
import com.partvision.compras.domain.CompraEstado;
import com.partvision.compras.domain.CompraLinea;
import com.partvision.compras.dto.*;
import com.partvision.compras.repository.CompraRepository;
import com.partvision.inventory.dto.EntradaRequest;
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
                .map(l -> l.codigo().toUpperCase())
                .collect(Collectors.toSet());

        Map<String, Producto> productosPorSku = productoRepo.findBySkuIn(codigos).stream()
                .collect(Collectors.toMap(
                        p -> p.getSku().toUpperCase(),
                        Function.identity(),
                        (a, b) -> a
                ));

        for (RecepcionLineaRequest lineaReq : request.lineas()) {
            CompraLinea linea = new CompraLinea();
            linea.setCodigo(lineaReq.codigo());
            linea.setDescripcion(lineaReq.descripcion());
            linea.setCantidad(lineaReq.cantidad());

            Producto producto = productosPorSku.get(lineaReq.codigo().toUpperCase());
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

        Ubicacion ubicacion = ubicacionService.getEntity(request.ubicacionId());
        compra.setEstado(CompraEstado.INGRESADA);
        compra.setUbicacionIngreso(ubicacion);

        int cargados = 0;
        for (CompraLinea linea : compra.getLineas()) {
            if (linea.getProducto() == null) continue;

            stockService.registrarEntrada(new EntradaRequest(
                    linea.getProducto().getId(),
                    ubicacion.getId(),
                    linea.getCantidad(),
                    "Compra factura #" + compra.getNumeroFactura()
            ));
            cargados++;
        }

        compraRepo.save(compra);
        log.info("Compra {} marcada INGRESADA: {} líneas con stock cargado en ubicación {}",
                compra.getNumeroFactura(), cargados, ubicacion.getCodigo());

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
        return CompraResponse.from(compra, true);
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
