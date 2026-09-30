package com.cloudstorage.api.repository;

import com.cloudstorage.api.entity.FileMetadata;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad {@link FileMetadata}. Proporciona operaciones CRUD estándar y
 * consultas personalizadas para la gestión de metadatos de archivos en el sistema.
 *
 * <p>Todas las consultas de listado excluyen archivos con soft delete (deletedAt IS NOT NULL) salvo
 * los métodos específicos de papelera.
 *
 * @author CloudStorage Team
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    /**
     * Busca todos los archivos activos de un propietario específico, ordenados por fecha de subida
     * en orden descendente (más recientes primero).
     *
     * @param ownerId el UUID del propietario de los archivos
     * @return lista de metadatos de archivos activos del propietario
     */
    List<FileMetadata> findByOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(UUID ownerId);

    /**
     * Busca archivos activos de un propietario con paginación y soporte de búsqueda por nombre de
     * archivo (insensible a mayúsculas/minúsculas).
     *
     * @param ownerId el UUID del propietario
     * @param search texto a buscar en el nombre original (puede ser vacío para todos)
     * @param pageable parámetros de paginación y ordenación
     * @return página de metadatos de archivos coincidentes
     */
    @Query(
            "SELECT f FROM FileMetadata f WHERE f.owner.id = :ownerId "
                    + "AND f.deletedAt IS NULL "
                    + "AND (:search IS NULL OR LOWER(f.originalName) LIKE LOWER(CONCAT('%', :search, '%'))) "
                    + "ORDER BY f.uploadedAt DESC")
    Page<FileMetadata> findByOwnerIdAndSearchTerm(UUID ownerId, String search, Pageable pageable);

    /**
     * Busca un archivo activo específico verificando tanto su ID como el ID del propietario. Útil
     * para garantizar que un usuario solo pueda acceder a sus propios archivos.
     *
     * @param id el UUID del archivo a buscar
     * @param ownerId el UUID del propietario esperado
     * @return un {@link Optional} que contiene los metadatos si el archivo existe y pertenece al
     *     propietario
     */
    Optional<FileMetadata> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    /**
     * Mantiene compatibilidad — busca archivo por id y ownerId incluyendo eliminados. Usado
     * internamente por operaciones de papelera.
     */
    Optional<FileMetadata> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Busca archivos activos del usuario que estén en la raíz (sin carpeta asociada). */
    List<FileMetadata> findByOwnerIdAndFolderIsNullAndDeletedAtIsNullOrderByUploadedAtDesc(
            UUID ownerId);

    /** Busca archivos activos del usuario que estén dentro de una carpeta específica. */
    List<FileMetadata> findByOwnerIdAndFolderIdAndDeletedAtIsNullOrderByUploadedAtDesc(
            UUID ownerId, UUID folderId);

    /** Mantiene compatibilidad con FolderService — busca archivos incluyendo eliminados. */
    List<FileMetadata> findByOwnerIdAndFolderIdOrderByUploadedAtDesc(UUID ownerId, UUID folderId);

    /**
     * Busca archivos eliminados (en papelera) del propietario.
     *
     * @param ownerId el UUID del propietario
     * @return lista de archivos en la papelera
     */
    List<FileMetadata> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID ownerId);

    /** Calcula el espacio ocupado por un usuario, incluida la papelera (sigue ocupando disco). */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f WHERE f.owner.id = :ownerId")
    Long sumFileSizeByOwnerId(UUID ownerId);

    /** Calcula el espacio ocupado en todo el servidor, incluida la papelera. */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f")
    Long sumAllFileSize();

    /** Cuenta el número total de archivos activos de un usuario. */
    @Query(
            "SELECT COUNT(f) FROM FileMetadata f WHERE f.owner.id = :ownerId AND f.deletedAt IS NULL")
    Long countActiveByOwnerId(UUID ownerId);

    /** Mantiene compatibilidad para la consulta de archivos en raíz — incluida en FolderService. */
    List<FileMetadata> findByOwnerIdAndFolderIsNullOrderByUploadedAtDesc(UUID ownerId);

    /** Número de archivos activos (fuera de la papelera) en todo el servidor. */
    long countByDeletedAtIsNull();

    /** Número de archivos en la papelera en todo el servidor. */
    long countByDeletedAtIsNotNull();
}
