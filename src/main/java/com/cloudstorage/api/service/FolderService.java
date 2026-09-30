package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.FileMetadataResponse;
import com.cloudstorage.api.dto.FolderContentsResponse;
import com.cloudstorage.api.dto.FolderResponse;
import com.cloudstorage.api.entity.FileMetadata;
import com.cloudstorage.api.entity.Folder;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.exception.ConflictException;
import com.cloudstorage.api.repository.FileMetadataRepository;
import com.cloudstorage.api.repository.FolderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicio para gestionar la lógica de carpetas e integración de contenidos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileStorageService fileStorageService;

    /** Crea una nueva carpeta en la ruta indicada. */
    @Transactional
    public FolderResponse createFolder(String name, UUID parentId, User owner) {
        String cleanName = name.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la carpeta no puede estar vacío");
        }

        ensureNameIsFree(cleanName, parentId, owner);

        Folder parent = null;
        if (parentId != null) {
            parent =
                    folderRepository
                            .findByIdAndOwnerId(parentId, owner.getId())
                            .orElseThrow(
                                    () ->
                                            new EntityNotFoundException(
                                                    "Carpeta padre no encontrada"));
        }

        Folder folder = Folder.builder().name(cleanName).parent(parent).owner(owner).build();

        Folder saved = folderRepository.save(folder);
        log.info(
                "Carpeta creada: id={}, nombre='{}', parentId={}, usuario={}",
                saved.getId(),
                saved.getName(),
                parentId,
                owner.getId());

        return FolderResponse.fromEntity(saved);
    }

    /** Renombra una carpeta existente. */
    @Transactional
    public FolderResponse renameFolder(UUID folderId, String newName, User owner) {
        Folder folder =
                folderRepository
                        .findByIdAndOwnerId(folderId, owner.getId())
                        .orElseThrow(() -> new EntityNotFoundException("Carpeta no encontrada"));

        String cleanName = newName.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("El nombre de la carpeta no puede estar vacío");
        }
        if (!cleanName.equals(folder.getName())) {
            UUID parentId = folder.getParent() != null ? folder.getParent().getId() : null;
            ensureNameIsFree(cleanName, parentId, owner);
        }

        folder.setName(cleanName);
        Folder saved = folderRepository.save(folder);
        log.info(
                "Carpeta renombrada: id={}, nuevoNombre='{}', usuario={}",
                folderId,
                cleanName,
                owner.getId());
        return FolderResponse.fromEntity(saved);
    }

    /**
     * Obtiene todos los contenidos (carpetas, archivos, breadcrumbs y almacenamiento) de un
     * directorio determinado.
     */
    /**
     * Lista todas las carpetas del usuario (sin jerarquía; cada una indica su {@code parentId}). La
     * interfaz la usa para elegir el destino al mover un archivo.
     *
     * @param owner el propietario
     * @return carpetas ordenadas por nombre
     */
    @Transactional(readOnly = true)
    public List<FolderResponse> listAllFolders(User owner) {
        return folderRepository.findByOwnerIdOrderByNameAsc(owner.getId()).stream()
                .map(FolderResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public FolderContentsResponse getFolderContents(UUID folderId, User owner) {
        FolderResponse currentFolderDto = null;
        List<FolderResponse> breadcrumbs = new ArrayList<>();

        if (folderId != null) {
            Folder currentFolder =
                    folderRepository
                            .findByIdAndOwnerId(folderId, owner.getId())
                            .orElseThrow(
                                    () -> new EntityNotFoundException("Carpeta no encontrada"));
            currentFolderDto = FolderResponse.fromEntity(currentFolder);

            Folder temp = currentFolder;
            while (temp != null) {
                breadcrumbs.add(FolderResponse.fromEntity(temp));
                temp = temp.getParent();
            }
            Collections.reverse(breadcrumbs);
        }

        List<Folder> folders =
                (folderId == null)
                        ? folderRepository.findByOwnerIdAndParentIsNull(owner.getId())
                        : folderRepository.findByOwnerIdAndParentId(owner.getId(), folderId);

        List<FolderResponse> folderResponses =
                folders.stream().map(FolderResponse::fromEntity).toList();

        List<FileMetadata> files =
                (folderId == null)
                        ? fileMetadataRepository
                                .findByOwnerIdAndFolderIsNullAndDeletedAtIsNullOrderByUploadedAtDesc(
                                        owner.getId())
                        : fileMetadataRepository
                                .findByOwnerIdAndFolderIdAndDeletedAtIsNullOrderByUploadedAtDesc(
                                        owner.getId(), folderId);

        List<FileMetadataResponse> fileResponses =
                files.stream().map(FileMetadataResponse::fromEntity).toList();

        Long storageUsed = fileMetadataRepository.sumFileSizeByOwnerId(owner.getId());

        return FolderContentsResponse.builder()
                .currentFolder(currentFolderDto)
                .folders(folderResponses)
                .files(fileResponses)
                .breadcrumbs(breadcrumbs)
                .storageUsed(storageUsed)
                .storageQuota(fileStorageService.getUserQuota())
                .build();
    }

    /** Elimina recursivamente una carpeta, todos sus archivos físicos y sus subcarpetas. */
    @Transactional
    public void deleteFolder(UUID folderId, User owner) {
        Folder folder =
                folderRepository
                        .findByIdAndOwnerId(folderId, owner.getId())
                        .orElseThrow(() -> new EntityNotFoundException("Carpeta no encontrada"));

        log.info(
                "Iniciando eliminación recursiva de la carpeta: id={}, nombre='{}', usuario={}",
                folder.getId(),
                folder.getName(),
                owner.getId());

        deleteFolderRecursively(folder, owner);
    }

    private void deleteFolderRecursively(Folder folder, User owner) {
        List<Folder> subfolders =
                folderRepository.findByOwnerIdAndParentId(owner.getId(), folder.getId());
        for (Folder sub : subfolders) {
            deleteFolderRecursively(sub, owner);
        }

        // Incluye los archivos que ya estaban en la papelera: todos se desvinculan de la carpeta
        // (si no, la clave foránea impediría borrarla) y los activos pasan a la papelera.
        // Al restaurarlos desde la papelera aparecerán en la raíz.
        List<FileMetadata> files =
                fileMetadataRepository.findByOwnerIdAndFolderIdOrderByUploadedAtDesc(
                        owner.getId(), folder.getId());
        LocalDateTime now = LocalDateTime.now();
        for (FileMetadata file : files) {
            if (file.getDeletedAt() == null) {
                file.setDeletedAt(now);
            }
            file.setFolder(null);
        }
        fileMetadataRepository.saveAll(files);

        folderRepository.delete(folder);
        log.info("Carpeta eliminada: id={}, nombre='{}'", folder.getId(), folder.getName());
    }

    /** Lanza 409 si ya hay una carpeta con ese nombre en la misma ubicación. */
    private void ensureNameIsFree(String name, UUID parentId, User owner) {
        boolean exists =
                (parentId == null)
                        ? folderRepository.existsByNameAndOwnerIdAndParentIsNull(
                                name, owner.getId())
                        : folderRepository.existsByNameAndOwnerIdAndParentId(
                                name, owner.getId(), parentId);
        if (exists) {
            throw new ConflictException(
                    "Ya existe una carpeta con el nombre '" + name + "' en esta ubicación");
        }
    }
}
