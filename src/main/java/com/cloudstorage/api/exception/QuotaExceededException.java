package com.cloudstorage.api.exception;

/**
 * No queda espacio en la cuota del usuario o en el límite de la licencia. Se responde con HTTP 507
 * Insufficient Storage.
 */
public class QuotaExceededException extends RuntimeException {

    public QuotaExceededException(String message) {
        super(message);
    }
}
