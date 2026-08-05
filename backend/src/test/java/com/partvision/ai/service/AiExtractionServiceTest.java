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
import com.partvision.catalog.domain.ProductoEstado;
import com.partvision.catalog.dto.ProductoRequest;
import com.partvision.catalog.dto.ProductoResponse;
import com.partvision.catalog.service.ProductoService;
import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.ResourceNotFoundException;
import com.partvision.inventory.domain.TipoMovimiento;
import com.partvision.inventory.dto.MovimientoResponse;
import com.partvision.inventory.service.StockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiExtractionServiceTest {

    @Mock private AiExtractionRepository extractionRepository;
    @Mock private StorageService storageService;
    @Mock private VisionExtractor visionExtractor;
    @Mock private ProductoService productoService;
    @Mock private StockService stockService;
    @Mock private AuditorAware<Long> auditorAware;
    @InjectMocks private AiExtractionService service;

    private AiExtraction pendiente() {
        return AiExtraction.builder().id(1L).imagenKey("k.jpg").modelo("stub-vision")
                .estado(EstadoExtraccion.PENDIENTE).build();
    }

    private ProductoRequest productoReq() {
        return new ProductoRequest(null, null, null, null, "Filtro de aceite", null, null, null, null);
    }

    private ProductoResponse productoResp() {
        return new ProductoResponse(50L, null, null, null, null, null, "Filtro de aceite",
                ProductoEstado.ACTIVO, Map.of(), List.of(), null);
    }

    @Test
    void extraer_creaBorradorPendiente() {
        when(storageService.store(any(), any())).thenReturn("uuid.jpg");
        when(visionExtractor.extraer(any(), any()))
                .thenReturn(new ExtraccionIA(null, null, null, null, Map.of(), "stub-vision"));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> {
            AiExtraction e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        MockMultipartFile img = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
        AiExtractionResponse r = service.extraer(img);

        assertThat(r.estado()).isEqualTo(EstadoExtraccion.PENDIENTE);
        assertThat(r.modelo()).isEqualTo("stub-vision");
        assertThat(r.datosSugeridos()).containsKeys("codigo_pieza", "marca", "descripcion", "codigo_barras", "detalles_extra");
    }

    @Test
    void extraer_imagenVacia_lanza422() {
        MockMultipartFile img = new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.extraer(img)).isInstanceOf(BusinessException.class);
    }

    @Test
    void extraer_imagenNull_lanza422() {
        assertThatThrownBy(() -> service.extraer(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void extraer_errorAlLeer_lanza422() throws IOException {
        MultipartFile img = mock(MultipartFile.class);
        when(img.isEmpty()).thenReturn(false);
        when(img.getBytes()).thenThrow(new IOException("io"));

        assertThatThrownBy(() -> service.extraer(img)).isInstanceOf(BusinessException.class);
    }

    @Test
    void confirmar_creaProductoSinStock() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendiente()));
        when(productoService.create(any())).thenReturn(productoResp());
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(9L));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmacionResponse r = service.confirmar(1L, new ConfirmarExtraccionRequest(productoReq(), null, null));

        assertThat(r.producto().id()).isEqualTo(50L);
        assertThat(r.movimiento()).isNull();
        verify(stockService, never()).registrarEntrada(any());
    }

    @Test
    void confirmar_creaProductoConStockInicial() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendiente()));
        when(productoService.create(any())).thenReturn(productoResp());
        when(stockService.registrarEntrada(any())).thenReturn(new MovimientoResponse(
                7L, 50L, TipoMovimiento.ENTRADA, 10, null, 3L, 9L, "Alta por extraccion IA #1", null, Instant.now()));
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(9L));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmacionResponse r = service.confirmar(1L, new ConfirmarExtraccionRequest(productoReq(), 3L, 10));

        assertThat(r.movimiento()).isNotNull();
        assertThat(r.movimiento().tipo()).isEqualTo(TipoMovimiento.ENTRADA);
    }

    @Test
    void confirmar_stockIncompleto_lanza422() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendiente()));

        assertThatThrownBy(() -> service.confirmar(1L, new ConfirmarExtraccionRequest(productoReq(), null, 10)))
                .isInstanceOf(BusinessException.class);
        verify(productoService, never()).create(any());
    }

    @Test
    void confirmar_extraccionYaProcesada_lanza422() {
        AiExtraction confirmada = pendiente();
        confirmada.setEstado(EstadoExtraccion.CONFIRMADA);
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(confirmada));

        assertThatThrownBy(() -> service.confirmar(1L, new ConfirmarExtraccionRequest(productoReq(), null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void descartar_marcaComoDescartada() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendiente()));
        when(extractionRepository.save(any(AiExtraction.class))).thenAnswer(inv -> inv.getArgument(0));

        AiExtractionResponse r = service.descartar(1L);

        assertThat(r.estado()).isEqualTo(EstadoExtraccion.DESCARTADA);
    }

    @Test
    void descartar_yaProcesada_lanza422() {
        AiExtraction descartada = pendiente();
        descartada.setEstado(EstadoExtraccion.DESCARTADA);
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(descartada));

        assertThatThrownBy(() -> service.descartar(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void findById_inexistente_lanza404() {
        when(extractionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_existente() {
        when(extractionRepository.findById(1L)).thenReturn(Optional.of(pendiente()));
        assertThat(service.findById(1L).id()).isEqualTo(1L);
    }

    @Test
    void listarPorEstado_devuelvePagina() {
        when(extractionRepository.findByEstadoOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pendiente())));

        var page = service.listarPorEstado(EstadoExtraccion.PENDIENTE, org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }
}
