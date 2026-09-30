package com.cloudstorage.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa los metadatos de un archivo almacenado en el sistema. Contiene información
 * sobre el nombre original, ruta de almacenamiento, tipo de contenido, tamaño, fecha de subida,
 * checksum y estado de eliminación suave.
 *
 * @author CloudStorage Team
 */
@Entity
@Table(
        name = "file_metadata",
        indexes = {
            @Index(name = "idx_file_owner_id", columnList = "owner_id"),
            @Index(name = "idx_file_folder_id", columnList = "folder_id"),
            @Index(name = "idx_file_deleted_at", columnList = "deleted_at"),
            @Index(name = "idx_file_original_name", columnList = "original_name")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    /** Identificador único del archivo generado automáticamente como UUID. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nombre original del archivo tal como fue subido por el usuario. */
    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** Ruta lógica donde se almacena el archivo en disco. */
    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    /** Tipo MIME del contenido del archivo (e.g., "application/pdf", "image/png"). */
    @Column(name = "content_type")
    private String contentType;

    /** Tamaño del archivo en bytes. */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** Hash SHA-256 del contenido original del archivo para verificación de integridad. */
    @Column(name = "checksum", length = 64)
    private String checksum;

    /**
     * Fecha y hora en que el archivo fue subido al sistema. Se establece automáticamente al
     * persistir y no puede ser actualizada.
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /**
     * Fecha y hora en que el archivo fue eliminado (soft delete). Si es null, el archivo está
     * activo y disponible.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Usuario propietario del archivo. Relación many-to-one con carga diferida (lazy loading). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Carpeta en la que se encuentra el archivo. Si es nula, el archivo está en la raíz de la
     * cuenta del usuario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    /** Callback de JPA que establece la fecha de subida antes de persistir la entidad. */
    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
