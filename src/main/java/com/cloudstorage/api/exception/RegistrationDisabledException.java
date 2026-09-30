package com.cloudstorage.api.exception;

/**
 * El registro de nuevos usuarios está deshabilitado (APP_REGISTRATION_ENABLED=false). Se responde
 * con HTTP 403 Forbidden.
 */
public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException(String message) {
        super(message);
    }
}
