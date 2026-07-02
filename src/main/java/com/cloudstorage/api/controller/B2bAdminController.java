package com.cloudstorage.api.controller;

import com.cloudstorage.api.service.B2bAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para el panel de configuración B2B de administradores.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
public class B2bAdminController {

    private final B2bAdminService b2bAdminService;

    /**
     * Verifica si la contraseña de administrador B2B es correcta.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPassword(@RequestBody PasswordRequest request) {
        if (request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Contraseña requerida"));
        }
        
        boolean ok = b2bAdminService.authenticate(request.getPassword());
        if (ok) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Autenticación correcta"));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Contraseña de administrador B2B incorrecta"));
        }
    }

    /**
     * Devuelve las variables configuradas actualmente en el archivo .env.
     */
    @GetMapping
    public ResponseEntity<?> getConfig(@RequestHeader(value = "X-B2B-Admin-Password", defaultValue = "") String adminPassword) {
        if (!b2bAdminService.authenticate(adminPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acceso denegado: Contraseña B2B incorrecta"));
        }
        
        try {
            Map<String, String> config = b2bAdminService.readConfig();
            // Creamos una copia filtrando el password B2B por seguridad (se gestiona aparte)
            Map<String, String> editableConfig = new HashMap<>(config);
            editableConfig.remove("B2B_ADMIN_PASSWORD");
            return ResponseEntity.ok(editableConfig);
        } catch (Exception e) {
            log.error("Error al leer la configuración: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo leer la configuración: " + e.getMessage()));
        }
    }

    /**
     * Guarda la nueva configuración .env y actualiza/valida parámetros en caliente.
     */
    @PostMapping
    public ResponseEntity<?> updateConfig(
            @RequestHeader(value = "X-B2B-Admin-Password", defaultValue = "") String adminPassword,
            @RequestBody UpdateConfigRequest request) {
        
        if (!b2bAdminService.authenticate(adminPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Acceso denegado: Contraseña B2B incorrecta"));
        }

        try {
            b2bAdminService.updateConfig(request.getConfig(), adminPassword, request.getNewAdminPassword());
            return ResponseEntity.ok(Map.of("success", true, "message", "Configuración actualizada correctamente en el archivo .env"));
        } catch (IllegalArgumentException e) {
            log.warn("Intento de guardado inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al actualizar la configuración .env: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo guardar la configuración: " + e.getMessage()));
        }
    }

    @Data
    public static class PasswordRequest {
        private String password;
    }

    @Data
    public static class UpdateConfigRequest {
        private Map<String, String> config;
        private String newAdminPassword;
    }
}
