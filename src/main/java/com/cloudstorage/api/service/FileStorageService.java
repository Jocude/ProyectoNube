package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.FileMetadataResponse;
import com.cloudstorage.api.entity.FileMetadata;
import com.cloudstorage.api.entity.Folder;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.exception.ConflictException;
import com.cloudstorage.api.exception.QuotaExceededException;
import com.cloudstorage.api.exception.StorageException;
import com.cloudstorage.api.repository.FileMetadataRepository;
import com.cloudstorage.api.repository.FolderRepository;
import com.cloudstorage.api.repository.ShareTokenRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Servicio principal de almacenamiento de archivos.
 *
 * <p>Gestiona las operaciones CRUD de archivos en el sistema de almacenamiento, incluyendo la
 * subida con cifrado AES-256-GCM, la descarga con descifrado, la eliminación suave (soft delete),
 * papelera, restauración y el listado paginado con búsqueda de archivos del usuario.
 *
 * <p>Los archivos se organizan en directorios por usuario y se almacenan cifrados en disco. Los
 * metadatos se persisten en la base de datos.
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
    private final ShareTokenRepository shareTokenRepository;
    private final EncryptionService encryptionService;
    private final FileValidationService fileValidationService;
    private final LicenseValidatorService licenseValidatorService;

    @Value("${app.storage.location}")
    private String storageLocation;

    @Value("${app.storage.quota-bytes}")
    private long userQuotaBytes;

    private Path rootLocation;

    /**
     * Registro inmutable que contiene los metadatos y los datos descifrados de un archivo cargado.
     *
     * @param metadata los metadatos del archivo
     * @param data los datos descifrados del archivo
     */
    public record LoadedFile(FileMetadata metadata, byte[] data) {}

    /** Inicializa el directorio raíz de almacenamiento, creándolo si no existe. */
    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
            log.info("Directorio de almacenamiento inicializado en: {}", rootLocation);
        } catch (IOException e) {
            log.error(
                    "No se pudo inicializar el directorio de almacenamiento: {}",
                    e.getMessage(),
                    e);
            throw new StorageException("No se pudo inicializar el directorio de almacenamiento");
        }
    }

    /**
     * Almacena un archivo subido por el usuario en la raíz, cifrándolo y guardando sus metadatos.
     *
     * @param file el archivo multipart subido
     * @param owner el usuario propietario del archivo
     * @return los metadatos del archivo almacenado como respuesta DTO
     */
    @Transactional
    public FileMetadataResponse store(MultipartFile file, User owner) {
        return store(file, owner, null);
    }

    /**
     * Almacena un archivo subido por el usuario en una carpeta específica, cifrándolo y guardando
     * sus metadatos. Valida la cuota de espacio disponible.
     *
     * @param file el archivo multipart subido
     * @param owner el usuario propietario del archivo
     * @param folderId el ID de la carpeta destino (opcional)
     * @return los metadatos del archivo almacenado
     */
    @Transactional
    public FileMetadataResponse store(MultipartFile file, User owner, UUID folderId) {
        // Validar el archivo
        fileValidationService.validateFile(file);

        // Validar cuota del usuario y límite global de la licencia
        checkQuota(owner, file.getSize());

        // Sanitizar el nombre original del archivo
        String originalFileName =
                fileValidationService.sanitizeFileName(file.getOriginalFilename());

        // Generar nombre único de almacenamiento
        String storedName = UUID.randomUUID() + "_" + originalFileName;

        // Resolver directorio del usuario usando su correo electrónico
        Path userDirectory = rootLocation.resolve(owner.getEmail());

        // Resolver la carpeta si se proporcionó un ID
        Folder folder = null;
        if (folderId != null) {
            folder =
                    folderRepository
                            .findByIdAndOwnerId(folderId, owner.getId())
                            .orElseThrow(
                                    () ->
                                            new EntityNotFoundException(
                                                    "Carpeta destino no encontrada"));
        }

        try {
            // Crear directorio del usuario si no existe
            Files.createDirectories(userDirectory);

            // Resolver ruta objetivo y validar contra path traversal
            Path targetPath = userDirectory.resolve(storedName).normalize();
            fileValidationService.validateStoragePath(targetPath, rootLocation);

            // Cifrar en streaming hacia disco, calculando a la vez el SHA-256 del original.
            // Así el archivo nunca se carga entero en memoria.
            MessageDigest sha256 = newSha256Digest();
            try (InputStream in = new DigestInputStream(file.getInputStream(), sha256);
                    OutputStream out =
                            new BufferedOutputStream(
                                    Files.newOutputStream(
                                            targetPath, StandardOpenOption.CREATE_NEW))) {
                encryptionService.encrypt(in, out);
            } catch (IOException | RuntimeException e) {
                // No dejar archivos cifrados a medias en disco
                Files.deleteIfExists(targetPath);
                throw e;
            }
            String checksum = HexFormat.of().formatHex(sha256.digest());
            // Si la transacción no llega a confirmarse, el archivo de disco sobra
            deleteFromDiskUnlessCommitted(targetPath);

            // Calcular ruta relativa para almacenar en base de datos
            String relativePath = rootLocation.relativize(targetPath).toString();

            // Crear entidad de metadatos (uploadedAt es gestionado por @PrePersist)
            FileMetadata metadata =
                    FileMetadata.builder()
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

            log.info(
                    "Archivo almacenado exitosamente: id={}, nombre='{}', tamaño={} bytes, checksum={}, usuario={}",
                    savedMetadata.getId(),
                    originalFileName,
                    file.getSize(),
                    checksum,
                    owner.getEmail());

            return FileMetadataResponse.fromEntity(savedMetadata);

        } catch (IOException e) {
            log.error(
                    "Error de E/S al almacenar el archivo '{}': {}",
                    originalFileName,
                    e.getMessage(),
                    e);
            throw new StorageException("Error al almacenar el archivo: " + originalFileName);
        }
    }

    /**
     * Carga un archivo del almacenamiento, descifrándolo para su descarga.
     *
     * @param fileId el identificador UUID del archivo
     * @param owner el usuario propietario del archivo
     * @return un registro {@link LoadedFile} con los metadatos y datos descifrados
     * @throws EntityNotFoundException si el archivo no existe o no pertenece al usuario
     * @throws StorageException si ocurre un error al leer o descifrar el archivo
     */
    @Transactional(readOnly = true)
    public LoadedFile loadAsResource(UUID fileId, User owner) {
        // Buscar metadatos del archivo (solo activos)
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Archivo no encontrado: id={}, usuario={}",
                                            fileId,
                                            owner.getId());
                                    return new EntityNotFoundException(
                                            "Archivo no encontrado con id: " + fileId);
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

            log.info(
                    "Archivo cargado exitosamente para descarga: id={}, nombre='{}', usuario={}",
                    fileId,
                    metadata.getOriginalName(),
                    owner.getId());

            return new LoadedFile(metadata, decryptedBytes);

        } catch (IOException e) {
            log.error(
                    "Error de E/S al cargar el archivo '{}': {}",
                    metadata.getOriginalName(),
                    e.getMessage(),
                    e);
            throw new StorageException("Error al leer el archivo: " + metadata.getOriginalName());
        }
    }

    /**
     * Carga los bytes cifrados en disco de un archivo para uso interno (e.g., compartición). No
     * requiere autenticación de usuario propietario (solo por token de compartición).
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
            log.error(
                    "Error de E/S al cargar el archivo '{}': {}",
                    metadata.getOriginalName(),
                    e.getMessage(),
                    e);
            throw new StorageException("Error al leer el archivo: " + metadata.getOriginalName());
        }
    }

    /**
     * Realiza un soft delete de un archivo (lo mueve a la papelera). El archivo físico se conserva
     * en disco; solo se marca como eliminado en la BD.
     *
     * @param fileId el identificador UUID del archivo a eliminar
     * @param owner el usuario propietario del archivo
     * @throws EntityNotFoundException si el archivo no existe o no pertenece al usuario
     */
    @Transactional
    public void delete(UUID fileId, User owner) {
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "Archivo no encontrado para eliminación: id={}, usuario={}",
                                            fileId,
                                            owner.getId());
                                    return new EntityNotFoundException(
                                            "Archivo no encontrado con id: " + fileId);
                                });

        // Soft delete: marcar como eliminado
        metadata.setDeletedAt(LocalDateTime.now());
        fileMetadataRepository.save(metadata);

        log.info(
                "Archivo movido a papelera: id={}, nombre='{}', usuario={}",
                fileId,
                metadata.getOriginalName(),
                owner.getId());
    }

    /**
     * Restaura un archivo desde la papelera (revierte el soft delete).
     *
     * @param fileId el identificador UUID del archivo a restaurar
     * @param owner el usuario propietario del archivo
     * @return los metadatos del archivo restaurado
     * @throws EntityNotFoundException si el archivo no existe en la papelera
     */
    @Transactional
    public FileMetadataResponse restoreFile(UUID fileId, User owner) {
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerId(fileId, owner.getId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Archivo no encontrado con id: " + fileId));

        if (metadata.getDeletedAt() == null) {
            throw new ConflictException("El archivo no está en la papelera");
        }

        metadata.setDeletedAt(null);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info(
                "Archivo restaurado desde la papelera: id={}, nombre='{}', usuario={}",
                fileId,
                metadata.getOriginalName(),
                owner.getId());

        return FileMetadataResponse.fromEntity(saved);
    }

    /**
     * Elimina permanentemente un archivo del disco y de la base de datos. El archivo debe estar en
     * la papelera (deletedAt != null) antes de eliminarse definitivamente.
     *
     * @param fileId el identificador UUID del archivo a eliminar permanentemente
     * @param owner el usuario propietario del archivo
     * @throws EntityNotFoundException si el archivo no existe
     */
    @Transactional
    public void hardDelete(UUID fileId, User owner) {
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerId(fileId, owner.getId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Archivo no encontrado con id: " + fileId));

        if (metadata.getDeletedAt() == null) {
            throw new ConflictException(
                    "El archivo debe estar en la papelera antes de eliminarse definitivamente");
        }

        // Primero la BD: los enlaces compartidos apuntan al archivo (clave foránea)
        shareTokenRepository.deleteByFileId(metadata.getId());
        fileMetadataRepository.delete(metadata);

        // El disco se borra solo cuando la BD ha confirmado el borrado; si algo fallara antes,
        // el archivo sigue intacto y consistente con sus metadatos.
        Path filePath = rootLocation.resolve(metadata.getStoredPath()).normalize();
        fileValidationService.validateStoragePath(filePath, rootLocation);
        deleteFromDiskAfterCommit(filePath);
        log.info(
                "Archivo eliminado definitivamente: id={}, nombre='{}', usuario={}",
                fileId,
                metadata.getOriginalName(),
                owner.getId());
    }

    /**
     * Lista todos los archivos activos del usuario con paginación y búsqueda opcional.
     *
     * @param owner el usuario propietario de los archivos
     * @param search texto de búsqueda por nombre (puede ser null o vacío para todos)
     * @param pageable parámetros de paginación
     * @return página de metadatos de archivos como respuestas DTO
     */
    @Transactional(readOnly = true)
    public Page<FileMetadataResponse> listFiles(User owner, String search, Pageable pageable) {
        Page<FileMetadata> files =
                fileMetadataRepository.findByOwnerIdAndSearchTerm(
                        owner.getId(),
                        (search == null || search.isBlank()) ? null : search,
                        pageable);

        log.debug(
                "Listando archivos para el usuario: {}, búsqueda='{}', total={}",
                owner.getId(),
                search,
                files.getTotalElements());

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
        List<FileMetadata> files =
                fileMetadataRepository.findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(
                        owner.getId());
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
        List<FileMetadata> files =
                fileMetadataRepository.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
                        owner.getId());
        return files.stream().map(FileMetadataResponse::fromEntity).toList();
    }

    /**
     * Renombra un archivo cambiando su nombre original.
     *
     * @param fileId el identificador del archivo
     * @param newName el nuevo nombre para el archivo
     * @param owner el usuario propietario
     * @return los metadatos actualizados del archivo
     */
    @Transactional
    public FileMetadataResponse renameFile(UUID fileId, String newName, User owner) {
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Archivo no encontrado con id: " + fileId));

        String sanitizedName = fileValidationService.sanitizeFileName(newName);
        if (sanitizedName.isBlank()) {
            throw new IllegalArgumentException("El nuevo nombre del archivo no es válido");
        }

        metadata.setOriginalName(sanitizedName);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info(
                "Archivo renombrado: id={}, nuevoNombre='{}', usuario={}",
                fileId,
                sanitizedName,
                owner.getId());
        return FileMetadataResponse.fromEntity(saved);
    }

    /**
     * Mueve un archivo a otra carpeta.
     *
     * @param fileId el identificador del archivo
     * @param targetFolderId la carpeta destino (null para raíz)
     * @param owner el usuario propietario
     * @return los metadatos actualizados del archivo
     */
    @Transactional
    public FileMetadataResponse moveFile(UUID fileId, UUID targetFolderId, User owner) {
        FileMetadata metadata =
                fileMetadataRepository
                        .findByIdAndOwnerIdAndDeletedAtIsNull(fileId, owner.getId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Archivo no encontrado con id: " + fileId));

        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder =
                    folderRepository
                            .findByIdAndOwnerId(targetFolderId, owner.getId())
                            .orElseThrow(
                                    () ->
                                            new EntityNotFoundException(
                                                    "Carpeta destino no encontrada"));
        }

        metadata.setFolder(targetFolder);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info(
                "Archivo movido: id={}, destino={}, usuario={}",
                fileId,
                targetFolderId != null ? targetFolderId : "raíz",
                owner.getId());
        return FileMetadataResponse.fromEntity(saved);
    }

    /** Busca un archivo por ID y owner para su uso en compartición (sin filtro de deletedAt). */
    @Transactional(readOnly = true)
    public FileMetadata findActiveById(UUID fileId, UUID ownerId) {
        return fileMetadataRepository
                .findByIdAndOwnerIdAndDeletedAtIsNull(fileId, ownerId)
                .orElseThrow(
                        () ->
                                new EntityNotFoundException(
                                        "Archivo no encontrado con id: " + fileId));
    }

    /**
     * Cuota de almacenamiento de cada usuario: la menor entre la configurada ({@code
     * APP_STORAGE_QUOTA}) y el límite total de la licencia.
     *
     * @return cuota por usuario en bytes
     */
    public long getUserQuota() {
        return Math.min(userQuotaBytes, licenseValidatorService.getAllowedQuota());
    }

    /**
     * Comprueba que caben {@code incomingBytes} más tanto en la cuota del usuario como en el límite
     * global de la licencia. Los archivos en la papelera cuentan, porque siguen en disco.
     *
     * @throws StorageException si se supera alguno de los dos límites
     */
    private void checkQuota(User owner, long incomingBytes) {
        long userUsed = fileMetadataRepository.sumFileSizeByOwnerId(owner.getId());
        long userQuota = getUserQuota();
        if (userUsed + incomingBytes > userQuota) {
            log.warn(
                    "Cuota de usuario excedida: usuario={}, solicitado={} bytes, usado={} bytes, cuota={} bytes",
                    owner.getId(),
                    incomingBytes,
                    userUsed,
                    userQuota);
            throw new QuotaExceededException(
                    "No hay suficiente espacio de almacenamiento disponible. Límite de "
                            + formatFileSize(userQuota)
                            + " excedido (la papelera también cuenta).");
        }

        long serverUsed = fileMetadataRepository.sumAllFileSize();
        long licensedQuota = licenseValidatorService.getAllowedQuota();
        if (serverUsed + incomingBytes > licensedQuota) {
            log.warn(
                    "Límite global de la licencia alcanzado: usado={} bytes, licencia={} bytes",
                    serverUsed,
                    licensedQuota);
            throw new QuotaExceededException(
                    "El servidor ha alcanzado el límite de almacenamiento de su licencia ("
                            + formatFileSize(licensedQuota)
                            + ").");
        }
    }

    /** Borra el archivo de disco si la transacción actual termina sin confirmarse. */
    private void deleteFromDiskUnlessCommitted(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteQuietly(path);
                        }
                    }
                });
    }

    /** Borra el archivo de disco cuando la transacción actual se confirme (o ya, si no hay). */
    private void deleteFromDiskAfterCommit(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(path);
                    }
                });
    }

    /**
     * Borra un archivo sin propagar errores: un archivo huérfano en disco no es grave, pero un
     * fallo aquí no debe deshacer una operación que la BD ya ha confirmado.
     */
    private static void deleteQuietly(Path path) {
        try {
            if (Files.deleteIfExists(path)) {
                log.info("Archivo eliminado del disco: '{}'", path);
            } else {
                log.warn("El archivo no existía en disco: '{}'", path);
            }
        } catch (IOException e) {
            log.error("No se pudo borrar el archivo '{}' del disco: {}", path, e.getMessage());
        }
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Todas las JVM están obligadas a incluir SHA-256
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cBiB", bytes / Math.pow(1024, exp), pre);
    }
}
