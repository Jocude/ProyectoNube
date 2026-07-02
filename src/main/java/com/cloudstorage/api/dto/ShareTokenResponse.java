package com.cloudstorage.api.dto;

import com.cloudstorage.api.entity.ShareToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de respuesta para un token de compartición de archivo.
 *
 * @author CloudStorage Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTokenResponse {

    private UUID id;
    private String token;
    private UUID fileId;
    private String fileName;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Integer maxDownloads;
    private int downloadCount;
    private boolean valid;

    public static ShareTokenResponse fromEntity(ShareToken entity) {
        return ShareTokenResponse.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .fileId(entity.getFile().getId())
                .fileName(entity.getFile().getOriginalName())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .maxDownloads(entity.getMaxDownloads())
                .downloadCount(entity.getDownloadCount())
                .valid(entity.isValid())
                .build();
    }
}
