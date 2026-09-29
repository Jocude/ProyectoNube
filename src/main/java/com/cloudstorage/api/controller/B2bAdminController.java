package com.cloudstorage.api.controller;

import com.cloudstorage.api.service.B2bAdminService;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el panel de configuración B2B de administradores.
 *
 * <p>Además del JWT de usuario, cada operación exige la contraseña de administración B2B. Los
 * intentos están limitados por IP en {@link com.cloudstorage.api.config.RateLimitFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class B2bAdminController {

    static final String ADMIN_PASSWORD_HEADER = "X-B2B-Admin-Password";

    private final B2bAdminService b2bAdminService;

    /** Verifica si la contraseña de administrador B2B es correcta. */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPassword(
            @RequestBody PasswordRequest request) {
        if (!b2bAdminService.authenticate(request.getPassword())) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Autenticación correcta"));
    }

    /** Devuelve la configuración visible (rutas y licencia; nunca secretos). */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig(
            @RequestHeader(value = ADMIN_PASSWORD_HEADER, defaultValue = "") String adminPassword) {
        if (!b2bAdminService.authenticate(adminPassword)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(new LinkedHashMap<>(b2bAdminService.readPublicConfig()));
        } catch (IOException e) {
            log.error("Error al leer la configuración: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo leer la configuración"));
        }
    }

    /** Actualiza la licencia y/o la contraseña de administración. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> updateConfig(
            @RequestHeader(value = ADMIN_PASSWORD_HEADER, defaultValue = "") String adminPassword,
            @RequestBody UpdateConfigRequest request) {
        if (!b2bAdminService.authenticate(adminPassword)) {
            return forbidden();
        }
        try {
            b2bAdminService.updateConfig(request.getLicenseKey(), request.getNewAdminPassword());
            return ResponseEntity.ok(
                    Map.of("success", true, "message", "Configuración actualizada correctamente"));
        } catch (IllegalArgumentException e) {
            log.warn("Intento de guardado inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error al actualizar el .env: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo guardar la configuración"));
        }
    }

    private static ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Contraseña de administrador B2B incorrecta"));
    }

    @Data
    public static class PasswordRequest {
        private String password;
    }

    @Data
    public static class UpdateConfigRequest {
        private String licenseKey;
        private String newAdminPassword;
    }
}
