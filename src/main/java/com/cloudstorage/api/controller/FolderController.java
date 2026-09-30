package com.cloudstorage.api.controller;

import com.cloudstorage.api.dto.RenameRequest;
import jakarta.validation.Valid;
import com.cloudstorage.api.dto.CreateFolderRequest;
import com.cloudstorage.api.dto.FolderContentsResponse;
import com.cloudstorage.api.dto.FolderResponse;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.service.FolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para la gestión de carpetas.
 */
@Tag(name = "Carpetas", description = "Operaciones de gestión de carpetas")
@Slf4j
@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /**
     * Crea una nueva carpeta para el usuario autenticado.
     */
    @Operation(summary = "Crear carpeta")
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @Valid @RequestBody CreateFolderRequest request,
            @AuthenticationPrincipal User currentUser) {

        log.info("Crear carpeta: nombre='{}', parentId={}, usuario={}",
                request.getName(), request.getParentId(), currentUser.getEmail());

        FolderResponse response = folderService.createFolder(request.getName(), request.getParentId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Renombra una carpeta existente.
     */
    @Operation(summary = "Renombrar carpeta")
    @PatchMapping("/{id}/rename")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable UUID id,
            @Valid @RequestBody RenameRequest body,
            @AuthenticationPrincipal User currentUser) {

        String newName = body.getName();

        log.info("Renombrar carpeta: id={}, nuevoNombre='{}', usuario={}", id, newName, currentUser.getEmail());
        FolderResponse response = folderService.renameFolder(id, newName, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el listado de archivos, carpetas y breadcrumbs del directorio actual.
     */
    @Operation(summary = "Listar contenidos de carpeta")
    @GetMapping("/contents")
    public ResponseEntity<FolderContentsResponse> getContents(
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @AuthenticationPrincipal User currentUser) {

        log.debug("Contenidos de carpeta: folderId={}, usuario={}", folderId, currentUser.getEmail());
        FolderContentsResponse response = folderService.getFolderContents(folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina recursivamente una carpeta y su contenido.
     */
    @Operation(summary = "Eliminar carpeta")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        log.info("Eliminar carpeta: id={}, usuario={}", id, currentUser.getEmail());
        folderService.deleteFolder(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
