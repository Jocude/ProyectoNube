package com.cloudstorage.api.dto;

import com.cloudstorage.api.entity.Folder;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO de respuesta para representar los datos públicos de una carpeta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {
    private UUID id;
    private String name;
    private UUID parentId;

    public static FolderResponse fromEntity(Folder entity) {
        if (entity == null) return null;
        return FolderResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .parentId(entity.getParent() != null ? entity.getParent().getId() : null)
                .build();
    }
}
