package com.cloudstorage.api.controller;

import com.cloudstorage.api.dto.MoveFileRequest;
import com.cloudstorage.api.dto.RenameRequest;
import jakarta.validation.Valid;
import com.cloudstorage.api.dto.FileMetadataResponse;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.service.FileStorageService;
import com.cloudstorage.api.service.FileStorageService.LoadedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para la gestión de archivos en el almacenamiento en la nube.
 *
 * <p>Expone los endpoints para subir, descargar, listar, buscar, renombrar,
 * mover y eliminar archivos. Todas las operaciones están autenticadas y aisladas
 * por usuario, de modo que cada usuario solo puede acceder a sus propios archivos.</p>
 *
 * <p>Endpoints disponibles:</p>
 * <ul>
 *   <li>{@code POST   /api/files/upload}         - Subir un archivo</li>
 *   <li>{@code GET    /api/files}                 - Listar archivos (paginado + búsqueda)</li>
 *   <li>{@code GET    /api/files/download/{id}}   - Descargar un archivo</li>
 *   <li>{@code GET    /api/files/preview/{id}}    - Vista previa en navegador</li>
 *   <li>{@code PATCH  /api/files/{id}/rename}     - Renombrar un archivo</li>
 *   <li>{@code PATCH  /api/files/{id}/move}       - Mover a otra carpeta</li>
 *   <li>{@code DELETE /api/files/{id}}            - Mover a papelera (soft delete)</li>
 * </ul>
 *
 * @author Cloud Storage API
 * @version 1.0
 */
@Tag(name = "Archivos", description = "Operaciones de gestión de archivos en la nube")
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * Sube un archivo al almacenamiento en la nube.
     *
     * <p>El archivo se valida, se cifra con AES-256-GCM y se almacena
     * en el directorio del usuario. Los metadatos se persisten en la
     * base de datos junto con el checksum SHA-256 del contenido original.</p>
     *
     * @param file        el archivo multipart a subir
     * @param folderId    ID de carpeta destino (opcional)
     * @param currentUser el usuario autenticado que realiza la operación
     * @return respuesta HTTP 201 (CREATED) con los metadatos del archivo subido
     */
    @Operation(summary = "Subir un archivo", description = "Sube y cifra un archivo al almacenamiento del usuario")
    @PostMapping("/upload")
    public ResponseEntity<FileMetadataResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @AuthenticationPrincipal User currentUser) {

        log.info("Solicitud de subida de archivo recibida: nombre='{}', tamaño={} bytes, folderId={}, usuario={}",
                file.getOriginalFilename(), file.getSize(), folderId, currentUser.getEmail());

        FileMetadataResponse response = fileStorageService.store(file, currentUser, folderId);

        log.info("Archivo subido exitosamente: id={}", response.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista los archivos del usuario autenticado con soporte de paginación y búsqueda.
     *
     * <p>Los archivos se devuelven ordenados por fecha de subida descendente
     * (más recientes primero). Si no se especifican parámetros de paginación,
     * se devuelve la primera página con 20 elementos.</p>
     *
     * @param page        número de página (base 0, por defecto 0)
     * @param size        tamaño de página (por defecto 20)
     * @param search      texto de búsqueda por nombre de archivo (opcional)
     * @param currentUser el usuario autenticado que realiza la consulta
     * @return respuesta HTTP 200 (OK) con la página de metadatos de archivos
     */
    @Operation(summary = "Listar archivos", description = "Lista los archivos del usuario con paginación y búsqueda por nombre")
    @GetMapping
    public ResponseEntity<Page<FileMetadataResponse>> listFiles(
            @Parameter(description = "Número de página (base 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elementos por página") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Texto a buscar en el nombre del archivo") @RequestParam(required = false) String search,
            @AuthenticationPrincipal User currentUser) {

        log.info("Solicitud de listado de archivos: usuario={}, page={}, size={}, search='{}'",
                currentUser.getId(), page, size, search);

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "uploadedAt"));
        Page<FileMetadataResponse> filesPage = fileStorageService.listFiles(currentUser, search, pageable);

        log.info("Listado completado: {} archivos (total={}), usuario={}",
                filesPage.getNumberOfElements(), filesPage.getTotalElements(), currentUser.getId());

        return ResponseEntity.ok(filesPage);
    }

    /**
     * Descarga un archivo del almacenamiento en la nube.
     *
     * <p>El archivo se descifra antes de enviarlo al cliente. Se establecen
     * las cabeceras HTTP apropiadas para forzar la descarga con el nombre
     * original del archivo.</p>
     *
     * @param id          el identificador UUID del archivo a descargar
     * @param currentUser el usuario autenticado que realiza la descarga
     * @return respuesta HTTP 200 (OK) con el contenido del archivo como bytes
     */
    @Operation(summary = "Descargar un archivo", description = "Descifra y descarga un archivo del almacenamiento")
    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Solicitud de descarga de archivo recibida: id={}, usuario={}", id, currentUser.getId());

        LoadedFile loadedFile = fileStorageService.loadAsResource(id, currentUser);

        return buildFileResponse(loadedFile, false);
    }

    /**
     * Sirve un archivo para vista previa en el navegador (inline).
     *
     * <p>A diferencia de la descarga, el navegador intentará mostrar el archivo
     * directamente (imágenes, PDFs, videos) en lugar de descargarlo.</p>
     *
     * @param id          el identificador UUID del archivo a previsualizar
     * @param currentUser el usuario autenticado
     * @return respuesta HTTP 200 (OK) con el contenido del archivo como bytes e inline disposition
     */
    @Operation(summary = "Vista previa de archivo", description = "Sirve el archivo para visualización directa en el navegador")
    @GetMapping("/preview/{id}")
    public ResponseEntity<byte[]> preview(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Solicitud de vista previa: id={}, usuario={}", id, currentUser.getId());

        LoadedFile loadedFile = fileStorageService.loadAsResource(id, currentUser);

        return buildFileResponse(loadedFile, true);
    }

    /**
     * Renombra un archivo.
     *
     * @param id          el identificador UUID del archivo
     * @param body        mapa con el campo "name" conteniendo el nuevo nombre
     * @param currentUser el usuario autenticado
     * @return respuesta HTTP 200 (OK) con los metadatos actualizados
     */
    @Operation(summary = "Renombrar un archivo")
    @PatchMapping("/{id}/rename")
    public ResponseEntity<FileMetadataResponse> rename(
            @PathVariable UUID id,
            @Valid @RequestBody RenameRequest body,
            @AuthenticationPrincipal User currentUser) {

        String newName = body.getName();
        log.info("Solicitud de renombrar archivo: id={}, nuevoNombre='{}', usuario={}", id, newName, currentUser.getId());

        FileMetadataResponse response = fileStorageService.renameFile(id, newName, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Mueve un archivo a otra carpeta.
     *
     * @param id          el identificador UUID del archivo
     * @param body        cuerpo con "folderId" (UUID de destino, o null para raíz)
     * @param currentUser el usuario autenticado
     * @return respuesta HTTP 200 (OK) con los metadatos actualizados
     */
    @Operation(summary = "Mover un archivo a otra carpeta")
    @PatchMapping("/{id}/move")
    public ResponseEntity<FileMetadataResponse> move(
            @PathVariable UUID id,
            @RequestBody MoveFileRequest body,
            @AuthenticationPrincipal User currentUser) {

        UUID targetFolderId = body.getFolderId();

        log.info("Solicitud de mover archivo: id={}, destino={}, usuario={}", id, targetFolderId, currentUser.getId());

        FileMetadataResponse response = fileStorageService.moveFile(id, targetFolderId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina un archivo moviéndolo a la papelera (soft delete).
     *
     * <p>El archivo no se borra físicamente del disco. Puede ser restaurado
     * desde la papelera o eliminado definitivamente desde el endpoint de trash.</p>
     *
     * @param id          el identificador UUID del archivo a eliminar
     * @param currentUser el usuario autenticado que realiza la eliminación
     * @return respuesta HTTP 204 (NO_CONTENT) sin cuerpo
     */
    @Operation(summary = "Eliminar archivo", description = "Mueve el archivo a la papelera (soft delete). Recuperable desde /api/trash")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Solicitud de eliminación (soft) de archivo: id={}, usuario={}", id, currentUser.getId());

        fileStorageService.delete(id, currentUser);

        log.info("Archivo movido a papelera: id={}, usuario={}", id, currentUser.getId());

        return ResponseEntity.noContent().build();
    }

    /**
     * Construye la respuesta HTTP con el contenido del archivo y las cabeceras apropiadas.
     *
     * @param loadedFile el archivo cargado con sus datos y metadatos
     * @param inline     si es true, usa Content-Disposition: inline (vista previa); si es false, usa attachment (descarga)
     * @return la respuesta HTTP configurada
     */
    private ResponseEntity<byte[]> buildFileResponse(LoadedFile loadedFile, boolean inline) {
        ContentDisposition contentDisposition = inline
                ? ContentDisposition.inline()
                        .filename(loadedFile.metadata().getOriginalName(), StandardCharsets.UTF_8)
                        .build()
                : ContentDisposition.attachment()
                        .filename(loadedFile.metadata().getOriginalName(), StandardCharsets.UTF_8)
                        .build();

        // Determinar el tipo de contenido
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (loadedFile.metadata().getContentType() != null
                && !loadedFile.metadata().getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(loadedFile.metadata().getContentType());
            } catch (Exception e) {
                log.warn("No se pudo parsear el tipo de contenido '{}', usando application/octet-stream",
                        loadedFile.metadata().getContentType());
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(mediaType);
        headers.setContentLength(loadedFile.data().length);

        // Añadir checksum si está disponible
        if (loadedFile.metadata().getChecksum() != null) {
            headers.add("X-File-Checksum-SHA256", loadedFile.metadata().getChecksum());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(loadedFile.data());
    }
}
