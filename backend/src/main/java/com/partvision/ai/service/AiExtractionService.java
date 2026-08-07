package com.partvision.ai.service;

import com.partvision.ai.domain.AiExtraction;
import com.partvision.ai.domain.EstadoExtraccion;
import com.partvision.ai.dto.AccionSugerida;
import com.partvision.ai.dto.AiExtractionResponse;
import com.partvision.ai.dto.CandidatoCoincidencia;
import com.partvision.ai.dto.ConfirmacionResponse;
import com.partvision.ai.dto.ConfirmarExtraccionRequest;
import com.partvision.ai.dto.SugerenciaAccionResponse;
import com.partvision.ai.repository.AiExtractionRepository;
import com.partvision.ai.storage.StorageService;
import com.partvision.ai.vision.ExtraccionIA;
import com.partvision.ai.vision.VisionExtractor;
import com.partvision.catalog.dto.ProductoCodigoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;

import java.util.Optional;
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
import java.util.List;
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

    /**
     * Analiza la extraccion contra el catalogo para sugerir que hacer: crear nuevo,
     * omitir por duplicado, o agregar el codigo de barras a un producto existente.
     */
    @Transactional(readOnly = true)
    public SugerenciaAccionResponse analizar(Long id) {
        AiExtraction extraccion = getEntity(id);
        Map<String, Object> datos = extraccion.getDatosSugeridos();
        String barcode = texto(datos.get("codigo_barras"));
        String sku = texto(datos.get("codigo_pieza"));
        String marca = texto(datos.get("marca"));

        // 1. El codigo de barras ya esta registrado en algun producto -> ya cargado.
        if (barcode != null) {
            Optional<ProductoResponse> porBarcode = productoService.buscarOpcionalPorCodigo(barcode);
            if (porBarcode.isPresent()) {
                ProductoResponse p = porBarcode.get();
                return new SugerenciaAccionResponse(AccionSugerida.YA_EXISTE, p.id(), p.descripcion(), barcode,
                        "El código de barras ya está registrado en '" + p.descripcion() + "'. No se vuelve a cargar.",
                        List.of());
            }
        }

        // 2. Match por SKU normalizado (tolera formato, respeta medida y marca).
        if (sku != null) {
            Optional<ProductoResponse> match = productoService.matchNormalizado(sku, marca);
            if (match.isPresent()) {
                ProductoResponse p = match.get();
                boolean yaTieneBarcode = barcode != null && p.codigos().stream()
                        .anyMatch(c -> barcode.equalsIgnoreCase(c.codigo()));
                if (barcode != null && !yaTieneBarcode) {
                    return new SugerenciaAccionResponse(AccionSugerida.AGREGAR_CODIGO, p.id(), p.descripcion(), barcode,
                            "'" + p.descripcion() + "' ya existe pero no tiene este código de barras. ¿Agregarlo?",
                            List.of());
                }
                return new SugerenciaAccionResponse(AccionSugerida.YA_EXISTE, p.id(), p.descripcion(), barcode,
                        "El producto '" + p.descripcion() + "' ya está cargado.", List.of());
            }
            // Sin match unico: mostrar variantes con la misma base (ej: otra medida) a revisar.
            List<ProductoResponse> similares = productoService.candidatosSimilares(sku);
            if (!similares.isEmpty()) {
                return posiblesCoincidencias(similares, barcode);
            }
        }

        // 3. No matchea ni comparte base de codigo -> producto nuevo.
        // (No se busca por descripcion: palabras como "SPARK"/"FOX"/"GOL" son nombres de
        //  modelos y generan falsos parecidos; el operario tiene la busqueda de texto manual.)
        return new SugerenciaAccionResponse(AccionSugerida.NUEVO, null, null, barcode,
                "Producto nuevo: se puede crear.", List.of());
    }

    private SugerenciaAccionResponse posiblesCoincidencias(List<ProductoResponse> productos, String barcode) {
        List<CandidatoCoincidencia> candidatos = productos.stream()
                .map(p -> new CandidatoCoincidencia(p.id(), p.sku(), p.descripcion(), p.marcaNombre(), p.proveedor()))
                .toList();
        String mensaje = candidatos.size() == 1
                ? "Puede que ya exista un producto parecido. Revisá si es el mismo antes de crear uno nuevo."
                : "Hay " + candidatos.size() + " productos parecidos. Revisá si alguno es el mismo antes de crear uno nuevo.";
        return new SugerenciaAccionResponse(AccionSugerida.POSIBLES_COINCIDENCIAS, null, null, barcode,
                mensaje, candidatos);
    }

    /**
     * "Completa" un producto existente con los codigos que detecto la extraccion y la
     * marca como CONFIRMADA contra ese producto (evita el duplicado). Adjunta lo que
     * haya y sea nuevo: el codigo de barras (tipo BARRA) y/o el codigo de pieza leido
     * en la etiqueta (tipo REF, como codigo alternativo de busqueda). Asi sirve tanto
     * para "rellenar el barcode" como para el caso sin barcode (solo nro de parte).
     */
    @Transactional
    public AiExtractionResponse asociarCodigo(Long id, Long productoId) {
        AiExtraction extraccion = getEntity(id);
        exigirPendiente(extraccion);
        String barcode = texto(extraccion.getDatosSugeridos().get("codigo_barras"));
        String sku = texto(extraccion.getDatosSugeridos().get("codigo_pieza"));
        if (barcode == null && sku == null) {
            throw new BusinessException("La extracción no tiene códigos detectados para asociar");
        }
        if (barcode != null && !productoService.existeCodigo(barcode)) {
            productoService.agregarCodigo(productoId, new ProductoCodigoRequest(barcode, "BARRA"));
        }
        if (sku != null && !productoService.existeCodigo(sku)) {
            productoService.agregarCodigo(productoId, new ProductoCodigoRequest(sku, "REF"));
        }

        extraccion.setEstado(EstadoExtraccion.CONFIRMADA);
        extraccion.setProductoId(productoId);
        extraccion.setUsuarioConfirmadorId(auditorAware.getCurrentAuditor().orElse(null));
        extraccion.setConfirmadoEn(Instant.now());
        return AiExtractionResponse.from(extractionRepository.save(extraccion));
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

    /** Lee un dato sugerido como texto no vacío, o null. */
    private String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
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
