package com.cloudstorage.api.exception;

/**
 * El enlace de compartición ha caducado o agotado sus descargas. Se responde con HTTP 410 Gone.
 */
public class ShareLinkExpiredException extends RuntimeException {

    public ShareLinkExpiredException(String message) {
        super(message);
    }
}
