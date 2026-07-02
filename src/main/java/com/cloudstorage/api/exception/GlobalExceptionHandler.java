package com.cloudstorage.api.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para la API REST.
 * Intercepta las excepciones lanzadas por los controladores y las transforma
 * en respuestas HTTP con un formato de error consistente.
 *
 * <p>Formato de respuesta de error estándar:</p>
 * <pre>
 * {
 *   "error": "Descripción del error",
 *   "details": { ... },  // opcional, presente en errores de validación
 *   "timestamp": "2024-01-01T00:00:00"
 * }
 * </pre>
 *
 * @author CloudStorage Team
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja excepciones de validación de archivos.
     * Retorna HTTP 400 (Bad Request) con el mensaje de validación.
     *
     * @param ex la excepción de validación de archivo
     * @return respuesta con estado 400 y detalles del error
     */
    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<Map<String, Object>> handleFileValidationException(FileValidationException ex) {
        log.warn("Error de validación de archivo: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja excepciones de almacenamiento.
     * Retorna HTTP 500 (Internal Server Error) cuando ocurre un fallo en las operaciones de I/O.
     *
     * @param ex la excepción de almacenamiento
     * @return respuesta con estado 500 y detalles del error
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, Object>> handleStorageException(StorageException ex) {
        log.error("Error de almacenamiento: {}", ex.getMessage(), ex);
        return buildErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Maneja excepciones de acceso denegado.
     * Retorna HTTP 403 (Forbidden) cuando el usuario no tiene permisos suficientes.
     *
     * @param ex la excepción de acceso denegado
     * @return respuesta con estado 403 y mensaje de acceso denegado
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return buildErrorResponse("Acceso denegado. No tiene permisos para realizar esta operación.", HttpStatus.FORBIDDEN);
    }

    /**
     * Maneja excepciones de entidad no encontrada.
     * Retorna HTTP 404 (Not Found) cuando no se encuentra el recurso solicitado.
     *
     * @param ex la excepción de entidad no encontrada
     * @return respuesta con estado 404 y detalles del error
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFoundException(EntityNotFoundException ex) {
        log.warn("Entidad no encontrada: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja excepciones de validación de argumentos de métodos (Bean Validation).
     * Retorna HTTP 400 (Bad Request) con un mapa detallado de errores por campo.
     *
     * @param ex la excepción de validación de argumentos
     * @return respuesta con estado 400 y mapa de errores de validación por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("Error de validación de argumentos: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Error de validación en los datos proporcionados");
        response.put("details", fieldErrors);
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Maneja excepciones de tamaño máximo de subida excedido.
     * Retorna HTTP 400 (Bad Request) cuando el archivo supera el tamaño permitido.
     *
     * @param ex la excepción de tamaño máximo excedido
     * @return respuesta con estado 400 y mensaje informativo
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Tamaño máximo de archivo excedido: {}", ex.getMessage());
        return buildErrorResponse("El archivo excede el tamaño máximo permitido.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja excepciones de cuenta bloqueada.
     * Retorna HTTP 423 (Locked) cuando la cuenta está temporalmente bloqueada.
     *
     * @param ex la excepción de cuenta bloqueada
     * @return respuesta con estado 423 y mensaje explicativo
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, Object>> handleLockedException(LockedException ex) {
        log.warn("Intento de acceso a cuenta bloqueada: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.valueOf(423));
    }

    /**
     * Maneja excepciones de límite de tasa superado.
     * Retorna HTTP 429 (Too Many Requests) cuando una IP excede el rate limit.
     *
     * @param ex la excepción de demasiadas peticiones
     * @return respuesta con estado 429 y mensaje informativo
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequestsException(TooManyRequestsException ex) {
        log.warn("Rate limit superado: {}", ex.getMessage());
        return buildErrorResponse(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Maneja excepciones de autenticación de Spring Security.
     * Retorna HTTP 401 (Unauthorized) cuando las credenciales son inválidas o falta autenticación.
     *
     * @param ex la excepción de autenticación
     * @return respuesta con estado 401 y mensaje de credenciales inválidas
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Error de autenticación: {}", ex.getMessage());
        return buildErrorResponse("Credenciales inválidas o autenticación requerida.", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Manejador genérico para cualquier excepción no capturada por los manejadores específicos.
     * Retorna HTTP 500 (Internal Server Error) como respuesta por defecto.
     *
     * @param ex la excepción no manejada
     * @return respuesta con estado 500 y mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Error interno no esperado: {}", ex.getMessage(), ex);
        return buildErrorResponse("Error interno del servidor. Por favor, intente más tarde.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Construye una respuesta de error con el formato estándar de la API.
     *
     * @param message el mensaje de error descriptivo
     * @param status  el código de estado HTTP a retornar
     * @return respuesta HTTP con el cuerpo de error formateado
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }
}
