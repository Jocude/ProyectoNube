package com.cloudstorage.api.exception;

/**
 * Excepción de tiempo de ejecución lanzada cuando un archivo no cumple
 * con las validaciones requeridas (formato, tamaño, nombre, etc.).
 *
 * @author CloudStorage Team
 */
public class FileValidationException extends RuntimeException {

    /**
     * Crea una nueva excepción de validación de archivo con el mensaje especificado.
     *
     * @param message descripción detallada del error de validación
     */
    public FileValidationException(String message) {
        super(message);
    }
}
