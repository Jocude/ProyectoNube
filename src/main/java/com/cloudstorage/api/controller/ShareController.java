package com.cloudstorage.api.controller;

import com.cloudstorage.api.dto.CreateShareRequest;
import com.cloudstorage.api.dto.ShareTokenResponse;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.service.ShareService;
import com.cloudstorage.api.service.ShareService.SharedFileResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la gestión de enlaces de compartición de archivos.
 *
 * <p>Permite crear, listar y revocar enlaces temporales para compartir archivos. El endpoint de
 * descarga pública ({@code /api/share/{token}}) no requiere autenticación.
 *
 * @author CloudStorage Team
 */
@Tag(name = "Compartición", description = "Gestión de enlaces de compartición de archivos")
@Slf4j
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * Crea un enlace de compartición para un archivo del usuario autenticado.
     *
     * @param fileId ID del archivo a compartir
     * @param body mapa con "expirationHours" (int, requerido) y "maxDownloads" (int, opcional)
     * @param currentUser el usuario autenticado
     * @return el token de compartición creado con la URL pública
     */
    @Operation(summary = "Crear enlace de compartición")
    @PostMapping("/files/{fileId}")
    public ResponseEntity<ShareTokenResponse> createShareLink(
            @PathVariable UUID fileId,
            @Valid @RequestBody(required = false) CreateShareRequest body,
            @AuthenticationPrincipal User currentUser) {

        // Sin cuerpo se usan los valores por defecto (24 h, descargas ilimitadas)
        CreateShareRequest request = body != null ? body : new CreateShareRequest();
        int expirationHours = request.getExpirationHours();
        Integer maxDownloads = request.getMaxDownloads();

        log.info(
                "Creando enlace de compartición: fileId={}, expHoras={}, maxDescargas={}, usuario={}",
                fileId,
                expirationHours,
                maxDownloads,
                currentUser.getEmail());

        ShareTokenResponse response =
                shareService.createShareLink(fileId, currentUser, expirationHours, maxDownloads);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todos los enlaces de compartición del usuario autenticado.
     *
     * @param currentUser el usuario autenticado
     * @return lista de tokens de compartición del usuario
     */
    @Operation(summary = "Listar enlaces de compartición propios")
    @GetMapping
    public ResponseEntity<List<ShareTokenResponse>> listShareLinks(
            @AuthenticationPrincipal User currentUser) {

        List<ShareTokenResponse> links = shareService.listShareLinks(currentUser);
        return ResponseEntity.ok(links);
    }

    /**
     * Descarga un archivo mediante un token de compartición público. Este endpoint NO requiere
     * autenticación JWT.
     *
     * @param token el valor del token de compartición
     * @return el contenido del archivo como bytes para descarga directa
     */
    @Operation(
            summary = "Descargar archivo por enlace público",
            description = "Endpoint público — no requiere autenticación")
    @GetMapping("/{token}")
    public ResponseEntity<byte[]> downloadSharedFile(@PathVariable String token) {
        log.info(
                "Solicitud de descarga por enlace compartido: token={}...",
                token.substring(0, Math.min(8, token.length())));

        SharedFileResult result = shareService.downloadSharedFile(token);

        ContentDisposition contentDisposition =
                ContentDisposition.attachment()
                        .filename(result.metadata().getOriginalName(), StandardCharsets.UTF_8)
                        .build();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (result.metadata().getContentType() != null
                && !result.metadata().getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(result.metadata().getContentType());
            } catch (Exception ignored) {
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(contentDisposition);
        headers.setContentType(mediaType);
        headers.setContentLength(result.data().length);

        return ResponseEntity.ok().headers(headers).body(result.data());
    }

    /**
     * Revoca un enlace de compartición.
     *
     * @param shareId ID del token de compartición a revocar
     * @param currentUser el usuario autenticado (debe ser el propietario)
     * @return HTTP 204 No Content
     */
    @Operation(summary = "Revocar enlace de compartición")
    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revokeShareLink(
            @PathVariable UUID shareId, @AuthenticationPrincipal User currentUser) {

        log.info(
                "Revocando enlace de compartición: shareId={}, usuario={}",
                shareId,
                currentUser.getEmail());
        shareService.revokeShareLink(shareId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
