package com.cloudstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO para la solicitud de creación de una carpeta. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {
    @NotBlank(message = "El nombre de la carpeta es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String name;

    private UUID parentId;
}
