package com.cloudstorage.api.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que encapsula el contenido de un directorio actual (subcarpetas y archivos), los breadcrumbs
 * para navegación y los detalles de cuota de espacio.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderContentsResponse {
    private FolderResponse currentFolder;
    private List<FolderResponse> folders;
    private List<FileMetadataResponse> files;
    private List<FolderResponse> breadcrumbs;
    private Long storageUsed;
    private Long storageQuota;
}
