package com.cloudstorage.api.exception;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador global de excepciones para la API REST.
 *
 * <p>{@code @RestControllerAdvice} hace que Spring llame a estos métodos cuando un controlador
 * lanza una excepción, y convierte cada tipo de excepción en su código HTTP. Hereda de {@link
 * ResponseEntityExceptionHandler}, que ya sabe tratar los errores estándar de Spring MVC (JSON mal
 * formado, UUID inválido en la URL, ruta inexistente, archivo demasiado grande...); aquí solo se
 * adapta su respuesta al formato común de la API:
 *
 * <pre>
 * {
 *   "error": "Descripción del error",
 *   "details": { ... },  // opcional, en errores de validación
 *   "timestamp": "2026-01-01T00:00:00"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- 4xx: errores del cliente ----------

    @ExceptionHandler({FileValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<Object> handleBadRequest(RuntimeException ex) {
        log.warn("Petición inválida: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Error de autenticación: {}", ex.getMessage());
        return buildErrorResponse("Credenciales inválidas.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Object> handleLockedException(LockedException ex) {
        log.warn("Intento de acceso a cuenta bloqueada: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.LOCKED);
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<Object> handleRegistrationDisabled(RegistrationDisabledException ex) {
        return buildErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return buildErrorResponse(
                "Acceso denegado. No tiene permisos para realizar esta operación.",
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFoundException(EntityNotFoundException ex) {
        log.warn("Entidad no encontrada: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        log.warn("Conflicto: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    /** Violación de una restricción de la BD (p. ej. nombre duplicado por una carrera). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return buildErrorResponse(
                "La operación entra en conflicto con datos existentes.", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ShareLinkExpiredException.class)
    public ResponseEntity<Object> handleShareLinkExpired(ShareLinkExpiredException ex) {
        log.info("Enlace de compartición no disponible: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.GONE);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequestsException(TooManyRequestsException ex) {
        log.warn("Rate limit superado: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- 5xx: errores del servidor ----------

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<Object> handleQuotaExceeded(QuotaExceededException ex) {
        log.warn("Cuota excedida: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.INSUFFICIENT_STORAGE);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Object> handleStorageException(StorageException ex) {
        log.error("Error de almacenamiento: {}", ex.getMessage(), ex);
        return buildErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex) {
        log.error("Error interno no esperado: {}", ex.getMessage(), ex);
        return buildErrorResponse(
                "Error interno del servidor. Por favor, intente más tarde.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ---------- Errores estándar de Spring MVC (heredados) ----------

    /** Errores de validación de {@code @Valid}: se devuelve el detalle por campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Error de validación: {}", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Error de validación en los datos proporcionados");
        body.put("details", fieldErrors);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    /** Resto de errores estándar de Spring MVC: mismo formato y mensaje en español. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        log.warn("Error de petición ({}): {}", statusCode.value(), ex.getMessage());
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return ResponseEntity.status(status).headers(headers).body(errorBody(messageFor(status)));
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado.";
            case METHOD_NOT_ALLOWED -> "Método HTTP no permitido para esta ruta.";
            case PAYLOAD_TOO_LARGE -> "El archivo excede el tamaño máximo permitido.";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado.";
            default ->
                    status.is4xxClientError()
                            ? "La petición no es válida."
                            : "Error interno del servidor. Por favor, intente más tarde.";
        };
    }

    private static ResponseEntity<Object> buildErrorResponse(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(errorBody(message));
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}
