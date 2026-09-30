package com.cloudstorage.api.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa un token de compartición de archivo.
 *
 * <p>Permite generar enlaces temporales para compartir archivos sin que el destinatario necesite
 * estar autenticado en el sistema. Cada token tiene una fecha de expiración y opcionalmente un
 * límite de descargas.
 *
 * @author CloudStorage Team
 */
@Entity
@Table(
        name = "share_tokens",
        indexes = {
            @Index(name = "idx_share_token_value", columnList = "token", unique = true),
            @Index(name = "idx_share_file_id", columnList = "file_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareToken {

    /** Identificador único del token de compartición. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** El valor del token (cadena aleatoria única usada en la URL pública). */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /** Archivo al que da acceso este token. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    /** Usuario que creó este token de compartición. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** Fecha y hora de expiración del token. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Número máximo de descargas permitidas. Si es null, el token no tiene límite de descargas. */
    @Column(name = "max_downloads")
    private Integer maxDownloads;

    /** Número de veces que se ha descargado el archivo con este token. */
    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private int downloadCount = 0;

    /** Fecha y hora de creación del token. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Callback de JPA que establece la fecha de creación antes de persistir la entidad. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Indica si el token está activo (no expirado y con descargas disponibles).
     *
     * @return {@code true} si el token es válido para su uso
     */
    public boolean isValid() {
        if (LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        if (maxDownloads != null && downloadCount >= maxDownloads) {
            return false;
        }
        return true;
    }
}
