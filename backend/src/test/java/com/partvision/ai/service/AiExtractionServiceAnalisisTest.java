package com.partvision.ai.service;

import com.partvision.ai.domain.AiExtraction;
import com.partvision.ai.domain.EstadoExtraccion;
import com.partvision.ai.dto.AccionSugerida;
import com.partvision.ai.dto.SugerenciaAccionResponse;
import com.partvision.ai.repository.AiExtractionRepository;
import com.partvision.ai.storage.StorageService;
import com.partvision.ai.vision.VisionExtractor;
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoCodigoResponse;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import com.partvision.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Que sugiere el sistema cuando la etiqueta trae solo algunos datos. La IA no decide:
 * propone, y estas ramas son las que arman esa propuesta.
 */
@ExtendWith(MockitoExtension.class)
class AiExtractionServiceAnalisisTest {

    @Mock private AiExtractionRepository extractionRepository;
    @Mock private StorageService storageService;
    @Mock private VisionExtractor visionExtractor;
    @Mock private ProductoService productoService;
    @Mock private StockService stockService;
    @Mock private AuditorAware<Long> auditorAware;
    @InjectMocks private AiExtractionService service;

    private AiExtraction pendienteCon(String barcode, String sku) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo_barras", barcode == null ? "" : barcode);
        datos.put("codigo_pieza", sku == null ? "" : sku);
        return AiExtraction.builder().id(1L).imagenKey("k.jpg").modelo("stub-vision")
                .estado(EstadoExtraccion.PENDIENTE).datosSugeridos(datos).build();
    }

    private ProductoResponse producto(Long id, String desc, String... codigos) {
        List<ProductoCodigoResponse> cs = java.util.Arrays.stream(codigos)
                .map(c -> new ProductoCodigoResponse(1L, c, "BARRA")).toList();
        return new ProductoResponse(id, null, null, null, null, null, desc,
                ProductoEstado.ACTIVO, Map.of(), cs, null, null, null, null, null);
    }

    /** Etiqueta sin numero de parte legible: no hay nada con que buscar en el catalogo. */
    @Test
    void sinSku_niSeBuscaEnElCatalogo() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon("779100", null)));
        when(productoService.buscarOpcionalPorCodigo("779100")).thenReturn(Optional.empty());

        SugerenciaAccionResponse r = service.analizar(1L);

        assertThat(r.accion()).isEqualTo(AccionSugerida.NUEVO);
        verify(productoService, never()).matchNormalizado(anyString(), any());
        verify(productoService, never()).candidatosSimilares(anyString());
    }

    /** Sin codigo de barras no hay nada que agregar: el producto ya esta cargado y listo. */
    @Test
    void skuMatcheaYNoHayBarcode_yaExiste() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon(null, "ABC-1")));
        when(productoService.matchNormalizado("ABC-1", null))
                .thenReturn(Optional.of(producto(50L, "Filtro")));

        SugerenciaAccionResponse r = service.analizar(1L);

        assertThat(r.accion()).isEqualTo(AccionSugerida.YA_EXISTE);
        assertThat(r.codigoBarras()).isNull();
    }

    /** Un solo parecido: el mensaje va en singular, sin inventar un numero. */
    @Test
    void unSoloParecido_mensajeEnSingular() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon(null, "813667+0.5")));
        when(productoService.matchNormalizado("813667+0.5", null)).thenReturn(Optional.empty());
        when(productoService.candidatosSimilares("813667+0.5"))
                .thenReturn(List.of(producto(70L, "AROS RECTIFICACION 813667 STD")));

        SugerenciaAccionResponse r = service.analizar(1L);

        assertThat(r.accion()).isEqualTo(AccionSugerida.POSIBLES_COINCIDENCIAS);
        assertThat(r.mensaje()).contains("Puede que ya exista un producto parecido");
        assertThat(r.candidatos()).hasSize(1);
    }

    // --- asociarCodigo ---

    @Test
    void asociarCodigo_soloBarcodeNuevo_loAgrega() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon("779100", null)));
        when(productoService.existeCodigo("779100")).thenReturn(false);
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(9L));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.asociarCodigo(1L, 50L);

        verify(productoService).agregarCodigo(anyLong(), any());
    }

    /** Codigos que el producto ya tiene no se vuelven a agregar. */
    @Test
    void asociarCodigo_codigosYaExistentes_noAgregaNada() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon("779100", "ABC-1")));
        when(productoService.existeCodigo("779100")).thenReturn(true);
        when(productoService.existeCodigo("ABC-1")).thenReturn(true);
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.asociarCodigo(1L, 50L);

        verify(productoService, never()).agregarCodigo(anyLong(), any());
    }

    @Test
    void asociarCodigo_soloSku_loAgregaComoReferencia() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendienteCon(null, "ABC-1")));
        when(productoService.existeCodigo("ABC-1")).thenReturn(false);
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(9L));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.asociarCodigo(1L, 50L);

        verify(productoService).agregarCodigo(anyLong(), any());
    }
}
