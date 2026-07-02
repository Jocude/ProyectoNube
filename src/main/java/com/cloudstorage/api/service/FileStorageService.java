package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.FileMetadataResponse;
import com.cloudstorage.api.entity.FileMetadata;
import com.cloudstorage.api.entity.Folder;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.exception.StorageException;
import com.cloudstorage.api.repository.FileMetadataRepository;
import com.cloudstorage.api.repository.FolderRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Servicio principal de almacenamiento de archivos.
 *
 * <p>Gestiona las operaciones CRUD de archivos en el sistema de almacenamiento,
 * incluyendo la subida con cifrado AES-256-GCM, la descarga con descifrado,
 * la eliminación suave (soft delete), papelera, restauración y el listado paginado
 * con búsqueda de archivos del usuario.</p>
 *
 * <p>Los archivos se organizan en directorios por usuario y se almacenan
 * cifrados en disco. Los metadatos se persisten en la base de datos.</p>
 *
 * @author Cloud Storage API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository folderRepository;
    private final EncryptionService encryptionService;
    private final FileValidationService fileValidationService;
    private final LicenseValidatorService licenseValidatorService;

    @Value("${app.storage.location}")
    private String storageLocation;

    private Path rootLocation;

    /**
     * Registro inmutable que contiene los metadatos y los datos descifrados de un archivo cargado.
     *
     * @param metadata los metadatos del archivo
     * @param data     los datos descifrados del archivo
     */
    public record LoadedFile(FileMetadata metadata, byte[] data) {
    }

    /**
     * Inicializa el directorio raíz de almacenamiento, creándolo si no existe.
     */
    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
            log.info("Directorio de almacenamiento inicializado en: {}", rootLocation);
        } catch (IOException e) {
            log.error("No se pudo inicializar el directorio de almacenamiento: {}", e.getMessage(), e);
            throw new StorageException("No se pudo inicializar el directorio de almacenamiento");
        }
    }

    /**
     * Almacena un archivo subido por el usuario en la raíz, cifrándolo y guardando sus metadatos.
     *
     * @param file  el archivo multipart subido
     * @param owner el usuario propietario del archivo
     * @return los metadatos del archivo almacenado como respuesta DTO
     */
    @Transactional
    public FileMetadataResponse store(MultipartFile file, User owner) {
        return store(file, owner, null);
    }

    /**
     * Almacena un archivo subido por el usuario en una carpeta específica,
     * cifrándolo y guardando sus metadatos. Valida la cuota de espacio disponible.
     *
     * @param file     el archivo multipart subido
     * @param owner    el usuario propietario del archivo
     * @param folderId el ID de la carpeta destino (opcional)
     * @return los metadatos del archivo almacenado
     */
    @Transactional
    public FileMetadataResponse store(MultipartFile file, User owner, UUID folderId) {
        // Validar el archivo
        fileValidationService.validateFile(file);

        // Validar límite de espacio (Cuota licenciada)
        Long allowedQuota = licenseValidatorService.getAllowedQuota();
        Long currentUsed = fileMetadataRepository.sumFileSizeByOwnerId(owner.getId());
        if (currentUsed + file.getSize() > allowedQuota) {
            log.warn("Límite de espacio excedido para el usuario {}: solicitado={} bytes, usado={} bytes, cuota={} bytes",
                    owner.getEmail(), file.getSize(), currentUsed, allowedQuota);
            throw new StorageException("No hay suficiente espacio de almacenamiento disponible. Límite de "
                    + formatFileSize(allowedQuota) + " excedido.");
        }

        // Sanitizar el nombre original del archivo
        String originalFileName = fileValidationService.sanitizeFileName(file.getOriginalFilename());

        // Generar nombre único de almacenamiento
        String storedName = UUID.randomUUID() + "_" + originalFileName;

        // Resolver directorio del usuario usando su correo electrónico
        Path userDirectory = rootLocation.resolve(owner.getEmail());

        // Resolver la carpeta si se proporcionó un ID
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerId(folderId, owner.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Carpeta destino no encontrada"));
        }

        try {
            // Crear directorio del usuario si no existe
            Files.createDirectories(userDirectory);

            // Resolver ruta objetivo y validar contra path traversal
            Path targetPath = userDirectory.resolve(storedName).normalize();
            fileValidationService.validateStoragePath(targetPath, rootLocation);

            // Leer bytes del archivo
            byte[] originalBytes = file.getBytes();

            // Calcular checksum SHA-256 del archivo original
            String checksum = computeSha256(originalBytes);

            // Cifrar y escribir en disco
            byte[] encryptedBytes = encryptionService.encrypt(originalBytes);
            Files.write(targetPath, encryptedBytes);

            // Calcular ruta relativa para almacenar en base de datos
            String relativePath = rootLocation.relativize(targetPath).toString();

            // Crear entidad de metadatos (uploadedAt es gestionado por @PrePersist)
            FileMetadata metadata = FileMetadata.builder()
                    .originalName(originalFileName)
                    .storedPath(relativePath)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .owner(owner)
                    .folder(folder)
                    .build();

            // Guardar metadatos en la base de datos
            FileMetadata savedMetadata = fileMetadataRepository.save(metadata);

            log.info("Archivo almacenado exitosamente: id={}, nombre='{}', tamaño={} bytes, checksum={}, usuario={}",
                    savedMetadata.getId(), originalFileName, file.getSize(), checksum, owner.getEmail());

            return FileMetadataResponse.fromEntity(savedMetadata);

        } catch (IOException e) {
            log.error("Error de E/S al almacenar el archivo '{}': {}", originalFileName, e.getMessage(), e);
            throw new StorageException("Error al almacenar el archivo: " + originalFileName);
        }
    }

    /**
     * Carga un archivo del almacenamiento, descifrándolo para su descarga.
     *
     * @param fileId el identificador UUID del archivo
     * @param owner  el usuario propietario del archivo
     * @return un registro {@link LoadedFile} con los metadatos y datos descifrados
     * @throws EntityNotFoundException si el archivo no existe o no pertenece al usuario
     * @throws StorageException        si ocurre un error al leer o descifrar el archivo
     */
    @Transactional(readOnly = true)
    public LoadedFile loadAsResource(UUID fileId, User owner) {
        // Buscar metadatos del archivo (solo activos)
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                .orElseThrow(() -> {
                    log.warn("Archivo no encontrado: id={}, usuario={}", fileId, owner.getId());
                    return new EntityNotFoundException("Archivo no encontrado con id: " + fileId);
                });

        try {
            // Resolver ruta almacenada
            Path filePath = rootLocation.resolve(metadata.getStoredPath()).normalize();

            // Validar contra path traversal
            fileValidationService.validateStoragePath(filePath, rootLocation);

            // Leer bytes cifrados del disco
            byte[] encryptedBytes = Files.readAllBytes(filePath);

            // Descifrar los bytes
            byte[] decryptedBytes = encryptionService.decrypt(encryptedBytes);

            log.info("Archivo cargado exitosamente para descarga: id={}, nombre='{}', usuario={}",
                    fileId, metadata.getOriginalName(), owner.getId());

            return new LoadedFile(metadata, decryptedBytes);

        } catch (IOException e) {
            log.error("Error de E/S al cargar el archivo '{}': {}", metadata.getOriginalName(), e.getMessage(), e);
            throw new StorageException("Error al leer el archivo: " + metadata.getOriginalName());
        }
    }

    /**
     * Carga los bytes cifrados en disco de un archivo para uso interno (e.g., compartición).
     * No requiere autenticación de usuario propietario (solo por token de compartición).
     *
     * @param metadata los metadatos del archivo a cargar
     * @return bytes descifrados del archivo
     */
    @Transactional(readOnly = true)
    public byte[] loadDecryptedBytes(FileMetadata metadata) {
        try {
            Path filePath = rootLocation.resolve(metadata.getStoredPath()).normalize();
            fileValidationService.validateStoragePath(filePath, rootLocation);
            byte[] encryptedBytes = Files.readAllBytes(filePath);
            return encryptionService.decrypt(encryptedBytes);
        } catch (IOException e) {
            log.error("Error de E/S al cargar el archivo '{}': {}", metadata.getOriginalName(), e.getMessage(), e);
            throw new StorageException("Error al leer el archivo: " + metadata.getOriginalName());
        }
    }

    /**
     * Realiza un soft delete de un archivo (lo mueve a la papelera).
     * El archivo físico se conserva en disco; solo se marca como eliminado en la BD.
     *
     * @param fileId el identificador UUID del archivo a eliminar
     * @param owner  el usuario propietario del archivo
     * @throws EntityNotFoundException si el archivo no existe o no pertenece al usuario
     */
    @Transactional
    public void delete(UUID fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                .orElseThrow(() -> {
                    log.warn("Archivo no encontrado para eliminación: id={}, usuario={}", fileId, owner.getId());
                    return new EntityNotFoundException("Archivo no encontrado con id: " + fileId);
                });

        // Soft delete: marcar como eliminado
        metadata.setDeletedAt(LocalDateTime.now());
        fileMetadataRepository.save(metadata);

        log.info("Archivo movido a papelera: id={}, nombre='{}', usuario={}",
                fileId, metadata.getOriginalName(), owner.getId());
    }

    /**
     * Restaura un archivo desde la papelera (revierte el soft delete).
     *
     * @param fileId el identificador UUID del archivo a restaurar
     * @param owner  el usuario propietario del archivo
     * @return los metadatos del archivo restaurado
     * @throws EntityNotFoundException si el archivo no existe en la papelera
     */
    @Transactional
    public FileMetadataResponse restoreFile(UUID fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Archivo no encontrado con id: " + fileId));

        if (metadata.getDeletedAt() == null) {
            throw new IllegalStateException("El archivo no está en la papelera");
        }

        metadata.setDeletedAt(null);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info("Archivo restaurado desde la papelera: id={}, nombre='{}', usuario={}",
                fileId, metadata.getOriginalName(), owner.getId());

        return FileMetadataResponse.fromEntity(saved);
    }

    /**
     * Elimina permanentemente un archivo del disco y de la base de datos.
     * El archivo debe estar en la papelera (deletedAt != null) antes de eliminarse definitivamente.
     *
     * @param fileId el identificador UUID del archivo a eliminar permanentemente
     * @param owner  el usuario propietario del archivo
     * @throws EntityNotFoundException si el archivo no existe
     */
    @Transactional
    public void hardDelete(UUID fileId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Archivo no encontrado con id: " + fileId));

        if (metadata.getDeletedAt() == null) {
            throw new IllegalStateException("El archivo debe estar en la papelera antes de eliminarse definitivamente");
        }

        try {
            Path filePath = rootLocation.resolve(metadata.getStoredPath()).normalize();
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Archivo eliminado físicamente del disco: '{}'", filePath);
            } else {
                log.warn("El archivo no existía en disco: '{}'", filePath);
            }
        } catch (IOException e) {
            log.error("Error de E/S al eliminar físicamente el archivo '{}': {}", metadata.getOriginalName(), e.getMessage(), e);
            throw new StorageException("Error al eliminar el archivo: " + metadata.getOriginalName());
        }

        fileMetadataRepository.delete(metadata);
        log.info("Archivo eliminado definitivamente: id={}, nombre='{}', usuario={}",
                fileId, metadata.getOriginalName(), owner.getId());
    }

    /**
     * Lista todos los archivos activos del usuario con paginación y búsqueda opcional.
     *
     * @param owner    el usuario propietario de los archivos
     * @param search   texto de búsqueda por nombre (puede ser null o vacío para todos)
     * @param pageable parámetros de paginación
     * @return página de metadatos de archivos como respuestas DTO
     */
    @Transactional(readOnly = true)
    public Page<FileMetadataResponse> listFiles(User owner, String search, Pageable pageable) {
        Page<FileMetadata> files = fileMetadataRepository.findByOwnerIdAndSearchTerm(
                owner.getId(),
                (search == null || search.isBlank()) ? null : search,
                pageable);

        log.debug("Listando archivos para el usuario: {}, búsqueda='{}', total={}",
                owner.getId(), search, files.getTotalElements());

        return files.map(FileMetadataResponse::fromEntity);
    }

    /**
     * Lista todos los archivos activos del usuario sin paginación.
     *
     * @param owner el usuario propietario de los archivos
     * @return lista completa de metadatos de archivos como respuestas DTO
     */
    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listFiles(User owner) {
        List<FileMetadata> files = fileMetadataRepository
                .findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(owner.getId());
        return files.stream().map(FileMetadataResponse::fromEntity).toList();
    }

    /**
     * Lista los archivos en la papelera (eliminados con soft delete) del usuario.
     *
     * @param owner el usuario propietario
     * @return lista de archivos en la papelera
     */
    @Transactional(readOnly = true)
    public List<FileMetadataResponse> listDeletedFiles(User owner) {
        List<FileMetadata> files = fileMetadataRepository
                .findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(owner.getId());
        return files.stream().map(FileMetadataResponse::fromEntity).toList();
    }

    /**
     * Renombra un archivo cambiando su nombre original.
     *
     * @param fileId      el identificador del archivo
     * @param newName     el nuevo nombre para el archivo
     * @param owner       el usuario propietario
     * @return los metadatos actualizados del archivo
     */
    @Transactional
    public FileMetadataResponse renameFile(UUID fileId, String newName, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Archivo no encontrado con id: " + fileId));

        String sanitizedName = fileValidationService.sanitizeFileName(newName);
        if (sanitizedName.isBlank()) {
            throw new IllegalArgumentException("El nuevo nombre del archivo no es válido");
        }

        metadata.setOriginalName(sanitizedName);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info("Archivo renombrado: id={}, nuevoNombre='{}', usuario={}", fileId, sanitizedName, owner.getId());
        return FileMetadataResponse.fromEntity(saved);
    }

    /**
     * Mueve un archivo a otra carpeta.
     *
     * @param fileId      el identificador del archivo
     * @param targetFolderId la carpeta destino (null para raíz)
     * @param owner       el usuario propietario
     * @return los metadatos actualizados del archivo
     */
    @Transactional
    public FileMetadataResponse moveFile(UUID fileId, UUID targetFolderId, User owner) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Archivo no encontrado con id: " + fileId));

        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndOwnerId(targetFolderId, owner.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Carpeta destino no encontrada"));
        }

        metadata.setFolder(targetFolder);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info("Archivo movido: id={}, destino={}, usuario={}",
                fileId, targetFolderId != null ? targetFolderId : "raíz", owner.getId());
        return FileMetadataResponse.fromEntity(saved);
    }

    /**
     * Busca un archivo por ID y owner para su uso en compartición (sin filtro de deletedAt).
     */
    @Transactional(readOnly = true)
    public FileMetadata findActiveById(UUID fileId, UUID ownerId) {
        return fileMetadataRepository.findByIdAndOwnerIdAndDeletedAtIsNull(fileId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Archivo no encontrado con id: " + fileId));
    }

    /**
     * Calcula el hash SHA-256 de un arreglo de bytes.
     *
     * @param data los bytes del archivo
     * @return representación hexadecimal del hash SHA-256
     */
    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 no disponible, omitiendo checksum");
            return null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cBiB", bytes / Math.pow(1024, exp), pre);
    }
}
