package com.cloudstorage.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para la solicitud de creación de una carpeta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {
    private String name;
    private UUID parentId;
}
