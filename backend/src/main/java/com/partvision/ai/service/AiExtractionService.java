package com.partvision.ai.service;

import com.partvision.ai.domain.AiExtraction;
import com.partvision.ai.domain.EstadoExtraccion;
import com.partvision.ai.dto.AiExtractionResponse;
import com.partvision.ai.dto.ConfirmacionResponse;
import com.partvision.ai.dto.ConfirmarExtraccionRequest;
import com.partvision.ai.repository.AiExtractionRepository;
import com.partvision.ai.storage.StorageService;
import com.partvision.ai.vision.ExtraccionIA;
import com.partvision.ai.vision.VisionExtractor;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.dto.EntradaRequest;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiExtractionService {

    private final AiExtractionRepository extractionRepository;
    private final StorageService storageService;
    private final VisionExtractor visionExtractor;
    private final ProductoService productoService;
    private final StockService stockService;
    private final AuditorAware<Long> auditorAware;

    @Transactional
    public AiExtractionResponse extraer(MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            throw new BusinessException("La imagen esta vacia");
        }
        byte[] bytes = leer(imagen);
        String key = storageService.store(bytes, imagen.getOriginalFilename());
        ExtraccionIA resultado = visionExtractor.extraer(bytes, imagen.getContentType());

        AiExtraction extraccion = extractionRepository.save(AiExtraction.builder()
                .imagenKey(key)
                .modelo(resultado.modelo())
                .datosSugeridos(aDatosSugeridos(resultado))
                .estado(EstadoExtraccion.PENDIENTE)
                .build());

        return AiExtractionResponse.from(extraccion);
    }

    @Transactional(readOnly = true)
    public AiExtractionResponse findById(Long id) {
        return AiExtractionResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<AiExtractionResponse> listarPorEstado(EstadoExtraccion estado, Pageable pageable) {
        return extractionRepository.findByEstadoOrderByCreatedAtDesc(estado, pageable)
                .map(AiExtractionResponse::from);
    }

    @Transactional
    public ConfirmacionResponse confirmar(Long id, ConfirmarExtraccionRequest request) {
        AiExtraction extraccion = getEntity(id);
        exigirPendiente(extraccion);
        validarStockInicial(request);

        ProductoResponse producto = productoService.create(request.producto());

        MovimientoResponse movimiento = null;
        if (request.cantidad() != null) {
            movimiento = stockService.registrarEntrada(new EntradaRequest(
                    producto.id(), request.ubicacionId(), request.cantidad(),
                    "Alta por extraccion IA #" + id));
        }

        extraccion.setEstado(EstadoExtraccion.CONFIRMADA);
        extraccion.setProductoId(producto.id());
        extraccion.setUsuarioConfirmadorId(auditorAware.getCurrentAuditor().orElse(null));
        extraccion.setConfirmadoEn(Instant.now());
        extractionRepository.save(extraccion);

        return new ConfirmacionResponse(id, producto, movimiento);
    }

    @Transactional
    public AiExtractionResponse descartar(Long id) {
        AiExtraction extraccion = getEntity(id);
        exigirPendiente(extraccion);
        extraccion.setEstado(EstadoExtraccion.DESCARTADA);
        return AiExtractionResponse.from(extractionRepository.save(extraccion));
    }

    private AiExtraction getEntity(Long id) {
        return extractionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Extraccion", id));
    }

    private void exigirPendiente(AiExtraction extraccion) {
        if (extraccion.getEstado() != EstadoExtraccion.PENDIENTE) {
            throw new BusinessException("La extraccion ya fue procesada (estado: " + extraccion.getEstado() + ")");
        }
    }

    private void validarStockInicial(ConfirmarExtraccionRequest request) {
        if ((request.cantidad() == null) != (request.ubicacionId() == null)) {
            throw new BusinessException("Cantidad y ubicacion deben indicarse juntas para cargar stock inicial");
        }
    }

    private byte[] leer(MultipartFile imagen) {
        try {
            return imagen.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer la imagen: " + ex.getMessage());
        }
    }

    private Map<String, Object> aDatosSugeridos(ExtraccionIA r) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo_pieza", r.codigoPieza());
        datos.put("marca", r.marca());
        datos.put("descripcion", r.descripcion());
        datos.put("codigo_barras", r.codigoBarras());
        datos.put("detalles_extra", r.detallesExtra());
        return datos;
    }
}
