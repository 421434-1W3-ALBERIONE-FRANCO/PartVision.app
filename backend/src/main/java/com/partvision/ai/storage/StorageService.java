package com.partvision.ai.storage;

/**
 * Puerto de almacenamiento de archivos. La implementacion actual guarda en disco
 * local; en produccion se reemplaza por una impl S3/MinIO detras de esta interfaz.
 */
public interface StorageService {

    /**
     * Guarda el contenido y devuelve la clave (referencia) para persistir en la BD.
     */
    String store(byte[] contenido, String nombreOriginal);
}
