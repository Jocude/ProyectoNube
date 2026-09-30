package com.cloudstorage.api.service;

import com.cloudstorage.api.exception.FileValidationException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Servicio de validación y sanitización de archivos.
 *
 * <p>Proporciona funcionalidades para validar archivos subidos por el usuario y sanitizar nombres
 * de archivo para prevenir ataques de inyección de rutas (Path Traversal) y otros vectores de
 * seguridad.
 *
 * <p><strong>Nota:</strong> Este servicio no impone límites de tamaño de archivo ni restricciones
 * de extensión, permitiendo la subida de cualquier tipo y tamaño de archivo según los requisitos
 * del sistema.
 *
 * @author Cloud Storage API
 * @version 1.0
 */
@Slf4j
@Service
public class FileValidationService {

    /** Longitud máxima permitida para nombres de archivo */
    private static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Sanitiza un nombre de archivo eliminando caracteres peligrosos y componentes de ruta para
     * prevenir ataques de inyección.
     *
     * <p>El proceso de sanitización incluye:
     *
     * <ul>
     *   <li>Extracción del nombre base del archivo (sin ruta)
     *   <li>Eliminación de bytes nulos
     *   <li>Reemplazo de separadores de ruta y secuencias de traversal
     *   <li>Eliminación de caracteres no permitidos
     *   <li>Recorte de puntos y espacios al inicio y final
     *   <li>Limitación de longitud a 255 caracteres
     * </ul>
     *
     * @param fileName el nombre de archivo original a sanitizar
     * @return el nombre de archivo sanitizado, o "unnamed_file" si el resultado está vacío
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unnamed_file";
        }

        String sanitized = fileName;

        // Extraer solo el nombre del archivo (sin ruta)
        int lastSeparatorIndex = Math.max(sanitized.lastIndexOf('/'), sanitized.lastIndexOf('\\'));
        if (lastSeparatorIndex >= 0) {
            sanitized = sanitized.substring(lastSeparatorIndex + 1);
        }

        // Eliminar bytes nulos
        sanitized = sanitized.replace("\0", "");

        // Reemplazar separadores de ruta y secuencias de path traversal
        sanitized = sanitized.replace("/", "_");
        sanitized = sanitized.replace("\\", "_");
        sanitized = sanitized.replace("..", "_");

        // Eliminar cualquier carácter que no sea alfanumérico, punto, guion, guion bajo o espacio
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9.\\-_ ]", "");

        // Recortar puntos y espacios al inicio y final
        sanitized = sanitized.replaceAll("^[. ]+", "");
        sanitized = sanitized.replaceAll("[. ]+$", "");

        // Si el resultado está vacío, usar nombre por defecto
        if (sanitized.isBlank()) {
            log.warn(
                    "El nombre de archivo quedó vacío después de la sanitización. Usando nombre por defecto.");
            return "unnamed_file";
        }

        // Limitar longitud a 255 caracteres
        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
            log.debug("Nombre de archivo truncado a {} caracteres", MAX_FILENAME_LENGTH);
        }

        log.debug("Nombre de archivo sanitizado: '{}' -> '{}'", fileName, sanitized);
        return sanitized;
    }

    /**
     * Valida un archivo subido por el usuario.
     *
     * <p>Verifica que el archivo no sea nulo ni esté vacío. No se aplican restricciones de tamaño
     * ni de tipo de archivo por diseño del sistema.
     *
     * @param file el archivo multipart a validar
     * @throws FileValidationException si el archivo es nulo o está vacío
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("El archivo está vacío o no fue proporcionado");
        }

        // Sanitizar y validar el nombre de archivo
        String originalFilename = file.getOriginalFilename();
        String sanitized = sanitizeFileName(originalFilename);

        log.debug(
                "Archivo validado correctamente: nombre='{}', tamaño={} bytes, tipo='{}'",
                sanitized,
                file.getSize(),
                file.getContentType());

        // No se aplica límite de tamaño (sin restricción por diseño)
        // No se aplica restricción de extensión (cualquier tipo de archivo permitido)
    }

    /**
     * Valida que una ruta de almacenamiento objetivo no escape del directorio base.
     *
     * <p>Previene ataques de Path Traversal verificando que la ruta normalizada del archivo
     * objetivo comience con la ruta normalizada del directorio base.
     *
     * @param targetPath la ruta objetivo a validar
     * @param baseDir el directorio base permitido
     * @throws FileValidationException si se detecta un intento de Path Traversal
     */
    public void validateStoragePath(Path targetPath, Path baseDir) {
        Path normalizedTarget = targetPath.normalize().toAbsolutePath();
        Path normalizedBase = baseDir.normalize().toAbsolutePath();

        if (!normalizedTarget.startsWith(normalizedBase)) {
            log.error(
                    "Intento de Path Traversal detectado. Ruta objetivo: '{}', Directorio base: '{}'",
                    normalizedTarget,
                    normalizedBase);
            throw new FileValidationException("Intento de Path Traversal detectado");
        }

        log.debug("Ruta de almacenamiento validada: '{}'", normalizedTarget);
    }
}
