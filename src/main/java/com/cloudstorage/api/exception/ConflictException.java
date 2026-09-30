package com.cloudstorage.api.exception;

/**
 * La operación choca con el estado actual del recurso (p. ej. email ya registrado o archivo fuera de la papelera). Se responde con HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
