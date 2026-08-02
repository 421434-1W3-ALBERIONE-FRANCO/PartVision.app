package com.partvision.imports.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.imports.dto.ImportResultResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Importacion masiva de productos desde CSV.
 * Cabecera esperada (case-insensitive): sku, marca, categoria, descripcion, codigo, tipoCodigo.
 * Solo "descripcion" es obligatoria. Cada fila se procesa de forma independiente:
 * el error de una no frena las demas (importacion parcial).
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ProductoImporter productoImporter;

    public ImportResultResponse importarCsv(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("El archivo esta vacio");
        }

        int total = 0;
        int importados = 0;
        List<ImportResultResponse.FilaError> errores = new ArrayList<>();

        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, formato)) {

            for (CSVRecord registro : parser) {
                total++;
                try {
                    productoImporter.importar(aFila(registro));
                    importados++;
                } catch (Exception ex) {
                    errores.add(new ImportResultResponse.FilaError(registro.getRecordNumber(), ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo CSV: " + ex.getMessage());
        }

        return new ImportResultResponse(total, importados, errores);
    }

    private ProductoImporter.FilaProducto aFila(CSVRecord registro) {
        String descripcion = valor(registro, "descripcion");
        if (descripcion == null) {
            throw new BusinessException("La descripcion es obligatoria");
        }
        return new ProductoImporter.FilaProducto(
                valor(registro, "sku"),
                valor(registro, "marca"),
                valor(registro, "categoria"),
                descripcion,
                valor(registro, "codigo"),
                valor(registro, "tipoCodigo"));
    }

    /** Devuelve el valor de la columna o null si no esta mapeada o esta en blanco. */
    private String valor(CSVRecord registro, String columna) {
        if (!registro.isMapped(columna) || !registro.isSet(columna)) {
            return null;
        }
        String v = registro.get(columna);
        return v.isBlank() ? null : v.trim();
    }
}
