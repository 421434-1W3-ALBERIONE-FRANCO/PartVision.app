package com.partvision.imports.service;

import com.partvision.common.exception.BusinessException;
import com.partvision.common.exception.DuplicateResourceException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Importacion masiva de productos desde CSV.
 * Cabecera esperada (case-insensitive): sku, marca, categoria, descripcion, codigo, tipoCodigo, proveedor.
 * Solo "descripcion" es obligatoria. Cada fila se procesa de forma independiente:
 * el error de una no frena las demas (importacion parcial).
 *
 * Deduplicacion: los catalogos de proveedor suelen traer el mismo producto repetido.
 * Se saltan los duplicados (dentro del archivo por clave, y contra la BD por la
 * restriccion de unicidad) y se cuentan como "omitidos", sin ensuciar los errores.
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
        int omitidos = 0;
        List<ImportResultResponse.FilaError> errores = new ArrayList<>();
        Set<String> clavesVistas = new HashSet<>();

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
                    ProductoImporter.FilaProducto fila = aFila(registro);
                    // Duplicado dentro del mismo archivo: se salta antes de tocar la BD.
                    if (!clavesVistas.add(claveDedup(fila))) {
                        omitidos++;
                        continue;
                    }
                    productoImporter.importar(fila);
                    importados++;
                } catch (DuplicateResourceException ex) {
                    // Duplicado contra lo que ya hay en la BD: se cuenta como omitido, no como error.
                    omitidos++;
                } catch (Exception ex) {
                    errores.add(new ImportResultResponse.FilaError(registro.getRecordNumber(), ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo CSV: " + ex.getMessage());
        }

        return new ImportResultResponse(total, importados, omitidos, errores);
    }

    /**
     * Clave para detectar duplicados dentro del archivo. Prioriza el codigo (unico
     * global); si no hay, usa sku+marca; y si tampoco, descripcion+marca.
     */
    private String claveDedup(ProductoImporter.FilaProducto fila) {
        if (fila.codigo() != null) {
            return "cod:" + fila.codigo().toLowerCase();
        }
        String marca = fila.marca() == null ? "" : fila.marca().toLowerCase();
        if (fila.sku() != null) {
            return "sku:" + fila.sku().toLowerCase() + "|" + marca;
        }
        return "desc:" + fila.descripcion().toLowerCase() + "|" + marca;
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
                valor(registro, "tipoCodigo"),
                valor(registro, "proveedor"));
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
