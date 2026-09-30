package com.cloudstorage.api.dto;

import java.util.UUID;
import lombok.Data;

/** DTO para mover un archivo. Un {@code folderId} nulo significa mover a la raíz. */
@Data
public class MoveFileRequest {

    private UUID folderId;
}
