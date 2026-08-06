package com.partvision.imports.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
import com.partvision.imports.dto.ImportResultResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock private ProductoImporter productoImporter;
    @InjectMocks private ImportService importService;

    private MockMultipartFile csv(String contenido) {
        return new MockMultipartFile("archivo", "productos.csv", "text/csv",
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void importar_dosFilasValidas() {
        String contenido = """
                sku,marca,categoria,descripcion,codigo,tipoCodigo
                ABC-1,Bosch,Filtros,Filtro de aceite,779100,EAN13
                XYZ-2,Fram,Filtros,Filtro de aire
                """;

        ImportResultResponse r = importService.importarCsv(csv(contenido));

        assertThat(r.totalFilas()).isEqualTo(2);
        assertThat(r.importados()).isEqualTo(2);
        assertThat(r.errores()).isEmpty();
        verify(productoImporter, org.mockito.Mockito.times(2)).importar(any());
    }

    @Test
    void importar_soloColumnaDescripcion() {
        ImportResultResponse r = importService.importarCsv(csv("descripcion\nRepuesto suelto\n"));

        assertThat(r.importados()).isEqualTo(1);
        assertThat(r.errores()).isEmpty();
    }

    @Test
    void importar_filaSinDescripcion_seReportaComoError() {
        String contenido = """
                sku,marca,categoria,descripcion,codigo,tipoCodigo
                ABC-1,Bosch,Filtros,,779100,EAN13
                """;

        ImportResultResponse r = importService.importarCsv(csv(contenido));

        assertThat(r.totalFilas()).isEqualTo(1);
        assertThat(r.importados()).isZero();
        assertThat(r.errores()).hasSize(1);
        assertThat(r.errores().get(0).mensaje()).contains("descripcion");
        verify(productoImporter, org.mockito.Mockito.never()).importar(any());
    }

    @Test
    void importar_duplicadoEnBd_seOmiteNoEsError() {
        doThrow(new DuplicateResourceException("El codigo ya esta registrado: 779100"))
                .when(productoImporter).importar(any());
        String contenido = """
                descripcion,codigo
                Filtro,779100
                """;

        ImportResultResponse r = importService.importarCsv(csv(contenido));

        assertThat(r.importados()).isZero();
        assertThat(r.omitidos()).isEqualTo(1);
        assertThat(r.errores()).isEmpty();
    }

    @Test
    void importar_duplicadoEnArchivo_seOmiteYNoLlamaAlImporter() {
        String contenido = """
                sku,marca,categoria,descripcion,codigo,tipoCodigo
                ,Bosch,Filtros,Filtro de aceite,779100,EAN13
                ,Bosch,Filtros,Filtro de aceite (repetido),779100,EAN13
                """;

        ImportResultResponse r = importService.importarCsv(csv(contenido));

        assertThat(r.totalFilas()).isEqualTo(2);
        assertThat(r.importados()).isEqualTo(1);
        assertThat(r.omitidos()).isEqualTo(1);
        assertThat(r.errores()).isEmpty();
        // El duplicado se salta antes de tocar la BD: el importer se llama una sola vez.
        verify(productoImporter, org.mockito.Mockito.times(1)).importar(any());
    }

    @Test
    void importar_errorRealDelImporter_seReportaComoError() {
        doThrow(new BusinessException("fallo raro"))
                .when(productoImporter).importar(any());
        String contenido = """
                descripcion,codigo
                Filtro,779100
                """;

        ImportResultResponse r = importService.importarCsv(csv(contenido));

        assertThat(r.importados()).isZero();
        assertThat(r.omitidos()).isZero();
        assertThat(r.errores()).hasSize(1);
        assertThat(r.errores().get(0).mensaje()).contains("fallo raro");
    }

    @Test
    void importar_archivoVacio_lanza422() {
        assertThatThrownBy(() -> importService.importarCsv(csv("")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void importar_archivoNull_lanza422() {
        assertThatThrownBy(() -> importService.importarCsv(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void importar_errorDeLectura_lanza422() throws IOException {
        MultipartFile archivo = mock(MultipartFile.class);
        when(archivo.isEmpty()).thenReturn(false);
        when(archivo.getInputStream()).thenThrow(new IOException("disco"));

        assertThatThrownBy(() -> importService.importarCsv(archivo))
                .isInstanceOf(BusinessException.class);
    }
}
