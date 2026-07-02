package com.cloudstorage.api.controller;

import com.cloudstorage.api.service.LicenseValidatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para la ruta raíz de la API.
 * <p>
 * Proporciona un mensaje de bienvenida, información de la versión,
 * estado del servicio, información de licencia y la URL pública del túnel.
 * </p>
 *
 * @author Cloud Storage API
 * @version 1.0
 */
@Tag(name = "Información", description = "Estado e información general de la API")
@RestController
@RequiredArgsConstructor
public class HomeController {

    private final LicenseValidatorService licenseValidatorService;

    /**
     * Endpoint raíz que devuelve metadatos de la API en formato JSON.
     *
     * @return ResponseEntity con información básica de la API y estado 200 (OK)
     */
    @Operation(summary = "Información de la API", description = "Devuelve el estado, versión, licencia y URL pública del servidor")
    @GetMapping("/api/info")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> info = new HashMap<>();
        info.put("app", "Cloud Storage API");
        info.put("description", "Servicio backend para almacenamiento en la nube seguro (Clon de Google Drive)");
        info.put("version", "1.0");
        info.put("status", "UP");

        // URL pública generada por el contenedor de túnel Cloudflare
        String storageLocation = System.getenv("APP_STORAGE_LOCATION");
        if (storageLocation == null || storageLocation.isEmpty()) {
            storageLocation = "./uploads";
        }
        String publicUrl = null;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(storageLocation, "public_url.txt");
            if (java.nio.file.Files.exists(path)) {
                publicUrl = java.nio.file.Files.readString(path).trim();
            }
        } catch (Exception e) {
            // Ignorar errores de lectura — el túnel puede no estar activo
        }
        info.put("publicUrl", publicUrl);

        // Información de licencia activa
        Map<String, Object> license = new HashMap<>();
        license.put("valid", licenseValidatorService.isLicenseValid());
        license.put("licensedTo", licenseValidatorService.getLicensedTo());
        license.put("storageQuotaBytes", licenseValidatorService.getAllowedQuota());
        info.put("license", license);

        // Endpoints disponibles
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("registro", "/api/auth/register");
        endpoints.put("login", "/api/auth/login");
        endpoints.put("archivos", "/api/files");
        endpoints.put("carpetas", "/api/folders");
        endpoints.put("papelera", "/api/trash");
        endpoints.put("comparticion", "/api/share");
        endpoints.put("swagger", "/swagger-ui.html");
        endpoints.put("health", "/actuator/health");
        info.put("endpoints", endpoints);

        return ResponseEntity.ok(info);
    }
}
