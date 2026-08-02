package com.partvision.ai.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @Test
    void store_guardaArchivoYConservaExtension(@TempDir Path dir) {
        LocalStorageService storage = new LocalStorageService(dir.toString());

        String key = storage.store(new byte[]{1, 2, 3}, "foto.jpg");

        assertThat(key).endsWith(".jpg");
        assertThat(Files.exists(dir.resolve(key))).isTrue();
    }

    @Test
    void store_sinExtensionNiNombre(@TempDir Path dir) {
        LocalStorageService storage = new LocalStorageService(dir.toString());

        assertThat(storage.store(new byte[]{1}, "sinpunto")).doesNotContain(".");
        assertThat(storage.store(new byte[]{1}, null)).isNotBlank();
    }

    @Test
    void store_errorDeIO_lanzaStorageException(@TempDir Path dir) throws IOException {
        // baseDir apunta a un archivo (no un directorio) -> createDirectories falla.
        Path archivo = Files.createFile(dir.resolve("noesdir"));
        LocalStorageService storage = new LocalStorageService(archivo.toString());

        assertThatThrownBy(() -> storage.store(new byte[]{1}, "x.jpg"))
                .isInstanceOf(StorageException.class);
    }
}
