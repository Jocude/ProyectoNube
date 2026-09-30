package com.cloudstorage.api.dto;

import com.cloudstorage.api.entity.FileMetadata;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta que contiene los metadatos públicos de un archivo. Excluye información sensible
 * como la ruta de almacenamiento y el propietario.
 *
 * @author CloudStorage Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataResponse {

    /** Identificador único del archivo. */
    private UUID id;

    /** Nombre original del archivo tal como fue subido por el usuario. */
    private String originalName;

    /** Tipo MIME del contenido del archivo. */
    private String contentType;

    /** Tamaño del archivo en bytes. */
    private Long fileSize;

    /** Fecha y hora en que el archivo fue subido al sistema. */
    private LocalDateTime uploadedAt;

    /**
     * Fecha y hora en que el archivo fue eliminado (soft delete). Null si el archivo está activo.
     */
    private LocalDateTime deletedAt;

    /** ID de la carpeta a la que pertenece el archivo. Null si está en la raíz. */
    private UUID folderId;

    /** Hash SHA-256 del contenido original para verificación de integridad. */
    private String checksum;

    /**
     * Método de fábrica que convierte una entidad {@link FileMetadata} en su representación DTO.
     * Mapea solo los campos públicos, omitiendo la ruta de almacenamiento y la referencia al
     * propietario.
     *
     * @param entity la entidad de metadatos del archivo a convertir
     * @return una instancia de {@link FileMetadataResponse} con los datos mapeados
     */
    public static FileMetadataResponse fromEntity(FileMetadata entity) {
        return FileMetadataResponse.builder()
                .id(entity.getId())
                .originalName(entity.getOriginalName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .uploadedAt(entity.getUploadedAt())
                .deletedAt(entity.getDeletedAt())
                .folderId(entity.getFolder() != null ? entity.getFolder().getId() : null)
                .checksum(entity.getChecksum())
                .build();
    }
}
