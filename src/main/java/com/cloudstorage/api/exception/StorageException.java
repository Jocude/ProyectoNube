package com.cloudstorage.api.exception;

/**
 * Excepción de tiempo de ejecución lanzada cuando ocurre un error durante las operaciones de
 * almacenamiento de archivos (lectura, escritura, eliminación).
 *
 * @author CloudStorage Team
 */
public class StorageException extends RuntimeException {

    /**
     * Crea una nueva excepción de almacenamiento con el mensaje especificado.
     *
     * @param message descripción detallada del error de almacenamiento
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Crea una nueva excepción de almacenamiento con el mensaje y la causa especificados.
     *
     * @param message descripción detallada del error de almacenamiento
     * @param cause la excepción original que provocó este error
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
