package com.cloudstorage.api.controller;

import com.cloudstorage.api.repository.FileMetadataRepository;
import com.cloudstorage.api.repository.UserRepository;
import com.cloudstorage.api.service.B2bAdminService;
import com.cloudstorage.api.service.LicenseValidatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador de métricas del sistema para el panel de administración B2B.
 *
 * <p>Todos los endpoints requieren la contraseña de administrador B2B en el header
 * {@code X-B2B-Admin-Password}.</p>
 *
 * @author CloudStorage Team
 */
@Tag(name = "Métricas Admin", description = "Métricas del sistema (acceso B2B restringido)")
@Slf4j
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final B2bAdminService b2bAdminService;
    private final UserRepository userRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final LicenseValidatorService licenseValidatorService;

    /**
     * Devuelve métricas de uso del sistema.
     *
     * @param adminPassword contraseña de administrador B2B
     * @return mapa con estadísticas del sistema
     */
    @Operation(summary = "Métricas del sistema", description = "Requiere contraseña B2B en header X-B2B-Admin-Password")
    @GetMapping
    public ResponseEntity<?> getMetrics(
            @RequestHeader(value = "X-B2B-Admin-Password", defaultValue = "") String adminPassword) {

        if (!b2bAdminService.authenticate(adminPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado: contraseña B2B incorrecta"));
        }

        long totalUsers = userRepository.count();
        long activeFiles = fileMetadataRepository.countByDeletedAtIsNull();
        long filesInTrash = fileMetadataRepository.countByDeletedAtIsNotNull();
        // Incluye la papelera: sigue ocupando disco y cuenta para el límite de la licencia
        long totalStorageUsed = fileMetadataRepository.sumAllFileSize();
        long quota = licenseValidatorService.getAllowedQuota();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalUsers", totalUsers);
        metrics.put("totalActiveFiles", activeFiles);
        metrics.put("totalFilesInTrash", filesInTrash);
        metrics.put("totalStorageUsedBytes", totalStorageUsed);
        metrics.put("storageQuotaBytes", quota);
        metrics.put("licensedTo", licenseValidatorService.getLicensedTo());
        metrics.put("licenseValid", licenseValidatorService.isLicenseValid());
        metrics.put(
                "storageUsedPercent",
                quota > 0 ? Math.round(totalStorageUsed * 1000.0 / quota) / 10.0 : 0.0);

        log.info("Métricas del sistema consultadas por admin B2B");
        return ResponseEntity.ok(metrics);
    }
}
