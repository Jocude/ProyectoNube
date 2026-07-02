package com.cloudstorage.api.exception;

/**
 * Excepción lanzada cuando se superan los límites de tasa de peticiones (rate limiting).
 *
 * @author CloudStorage Team
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
