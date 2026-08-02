package com.partvision.ai.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Almacenamiento en disco local (dev/MVP). Se reemplaza por S3/MinIO en produccion.
 */
@Service
public class LocalStorageService implements StorageService {

    private final Path baseDir;

    public LocalStorageService(@Value("${storage.local.dir:${java.io.tmpdir}/partvision-uploads}") String dir) {
        this.baseDir = Path.of(dir);
    }

    @Override
    public String store(byte[] contenido, String nombreOriginal) {
        try {
            Files.createDirectories(baseDir);
            String key = UUID.randomUUID() + extension(nombreOriginal);
            Files.write(baseDir.resolve(key), contenido);
            return key;
        } catch (IOException ex) {
            throw new StorageException("No se pudo guardar el archivo: " + ex.getMessage(), ex);
        }
    }

    private String extension(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        int punto = nombreOriginal.lastIndexOf('.');
        return punto >= 0 ? nombreOriginal.substring(punto) : "";
    }
}
