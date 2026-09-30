package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.ShareTokenResponse;
import com.cloudstorage.api.entity.FileMetadata;
import com.cloudstorage.api.entity.ShareToken;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.exception.ShareLinkExpiredException;
import com.cloudstorage.api.repository.ShareTokenRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de gestión de links de compartición de archivos.
 *
 * <p>Permite generar tokens temporales para compartir archivos públicamente
 * sin requerir autenticación. Los tokens tienen expiración configurable y
 * pueden tener un límite máximo de descargas.</p>
 *
 * @author CloudStorage Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareTokenRepository shareTokenRepository;
    private final FileStorageService fileStorageService;

    /**
     * Crea un nuevo enlace de compartición para un archivo.
     *
     * @param fileId           el ID del archivo a compartir
     * @param owner            el usuario propietario del archivo
     * @param expirationHours  horas hasta que expire el enlace (1-720)
     * @param maxDownloads     número máximo de descargas (null = ilimitado)
     * @return el token de compartición creado
     */
    @Transactional
    public ShareTokenResponse createShareLink(UUID fileId, User owner, int expirationHours, Integer maxDownloads) {
        if (expirationHours < 1 || expirationHours > 720) {
            throw new IllegalArgumentException("La expiración debe estar entre 1 y 720 horas");
        }

        FileMetadata file = fileStorageService.findActiveById(fileId, owner.getId());

        String tokenValue = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        ShareToken shareToken = ShareToken.builder()
                .token(tokenValue)
                .file(file)
                .owner(owner)
                .expiresAt(LocalDateTime.now().plusHours(expirationHours))
                .maxDownloads(maxDownloads)
                .build();

        ShareToken saved = shareTokenRepository.save(shareToken);
        log.info("Enlace de compartición creado: tokenId={}, fileId={}, expira={}, maxDescargas={}, usuario={}",
                saved.getId(), fileId, saved.getExpiresAt(), maxDownloads, owner.getEmail());

        return ShareTokenResponse.fromEntity(saved);
    }

    /**
     * Obtiene los bytes descifrados del archivo asociado a un token de compartición.
     * Valida que el token exista, no haya expirado y no haya superado el límite de descargas.
     *
     * @param tokenValue el valor del token de compartición
     * @return los bytes descifrados del archivo y sus metadatos
     */
    @Transactional
    public SharedFileResult downloadSharedFile(String tokenValue) {
        ShareToken shareToken = shareTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new EntityNotFoundException("Enlace de compartición no válido o inexistente"));

        if (!shareToken.isValid()) {
            log.warn("Intento de descarga con token inválido o expirado: tokenId={}", shareToken.getId());
            throw new ShareLinkExpiredException(
                    "Este enlace de compartición ha expirado o ha alcanzado el límite de descargas");
        }

        // Un archivo en la papelera deja de estar disponible por sus enlaces
        if (shareToken.getFile().getDeletedAt() != null) {
            throw new ShareLinkExpiredException("El archivo compartido ya no está disponible");
        }

        byte[] data = fileStorageService.loadDecryptedBytes(shareToken.getFile());

        shareToken.setDownloadCount(shareToken.getDownloadCount() + 1);
        shareTokenRepository.save(shareToken);

        log.info("Descarga por enlace compartido: tokenId={}, fileId={}, descarga #{}",
                shareToken.getId(), shareToken.getFile().getId(), shareToken.getDownloadCount());

        return new SharedFileResult(shareToken.getFile(), data);
    }

    /**
     * Lista todos los tokens de compartición creados por el usuario.
     *
     * @param owner el usuario propietario
     * @return lista de tokens de compartición
     */
    @Transactional(readOnly = true)
    public List<ShareTokenResponse> listShareLinks(User owner) {
        return shareTokenRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(ShareTokenResponse::fromEntity)
                .toList();
    }

    /**
     * Revoca un enlace de compartición eliminándolo de la base de datos.
     *
     * @param shareId el ID del token a revocar
     * @param owner   el usuario propietario (para verificar autorización)
     */
    @Transactional
    public void revokeShareLink(UUID shareId, User owner) {
        // Un enlace ajeno se trata igual que uno inexistente, para no revelar que existe
        ShareToken token = shareTokenRepository.findById(shareId)
                .filter(t -> t.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Enlace no encontrado: " + shareId));

        shareTokenRepository.delete(token);
        log.info("Enlace de compartición revocado: tokenId={}, usuario={}", shareId, owner.getEmail());
    }

    /**
     * Registro inmutable con el archivo y sus datos para descarga compartida.
     */
    public record SharedFileResult(FileMetadata metadata, byte[] data) {}
}
