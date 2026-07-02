package com.cloudstorage.api.controller;

import com.cloudstorage.api.dto.FileMetadataResponse;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controlador REST para la gestión de la papelera de reciclaje.
 *
 * <p>Permite listar archivos eliminados (soft delete), restaurarlos o eliminarlos
 * permanentemente del disco y la base de datos.</p>
 *
 * @author CloudStorage Team
 */
@Tag(name = "Papelera", description = "Gestión de la papelera de reciclaje")
@Slf4j
@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final FileStorageService fileStorageService;

    /**
     * Lista todos los archivos en la papelera del usuario.
     *
     * @param currentUser el usuario autenticado
     * @return lista de archivos eliminados (con soft delete)
     */
    @Operation(summary = "Listar papelera", description = "Devuelve todos los archivos eliminados recientemente")
    @GetMapping
    public ResponseEntity<List<FileMetadataResponse>> listTrash(
            @AuthenticationPrincipal User currentUser) {

        log.info("Listando papelera para usuario: {}", currentUser.getId());
        List<FileMetadataResponse> deleted = fileStorageService.listDeletedFiles(currentUser);
        return ResponseEntity.ok(deleted);
    }

    /**
     * Restaura un archivo de la papelera, haciéndolo accesible nuevamente.
     *
     * @param id          el identificador UUID del archivo a restaurar
     * @param currentUser el usuario autenticado
     * @return los metadatos del archivo restaurado
     */
    @Operation(summary = "Restaurar archivo", description = "Saca un archivo de la papelera y lo restaura")
    @PostMapping("/{id}/restore")
    public ResponseEntity<FileMetadataResponse> restoreFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Restaurando archivo desde papelera: id={}, usuario={}", id, currentUser.getId());
        FileMetadataResponse response = fileStorageService.restoreFile(id, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina permanentemente un archivo de la papelera (del disco y la base de datos).
     *
     * @param id          el identificador UUID del archivo a eliminar definitivamente
     * @param currentUser el usuario autenticado
     * @return HTTP 204 No Content
     */
    @Operation(summary = "Eliminar permanentemente", description = "Borra el archivo del disco de forma definitiva e irreversible")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDelete(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Eliminación permanente de archivo: id={}, usuario={}", id, currentUser.getId());
        fileStorageService.hardDelete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
