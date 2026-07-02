package com.cloudstorage.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2bAdminService {

    private final LicenseValidatorService licenseValidatorService;
    private static final String DEFAULT_ADMIN_PASSWORD = "admin admin";

    /**
     * Resuelve la ruta del archivo .env activo en el sistema.
     */
    private Path getEnvFilePath() {
        File envInContainer = new File("/app/.env");
        if (envInContainer.exists()) {
            return envInContainer.toPath();
        }
        // Fallback para ejecución local en desarrollo
        return Path.of(".env");
    }

    /**
     * Lee todos los parámetros actuales del archivo .env.
     */
    public Map<String, String> readConfig() throws IOException {
        Path envPath = getEnvFilePath();
        Map<String, String> config = new LinkedHashMap<>();

        // Valores por defecto
        config.put("HOST_STORAGE_PATH", "./uploads");
        config.put("HOST_DB_PATH", "./pgdata");
        config.put("DB_PASSWORD", "securepassword123");
        config.put("JWT_SECRET", "defaultJwtSecretKeyThatShouldBeChangedInProduction2026!");
        config.put("ENCRYPTION_KEY", "dGhpcyBpcyBhIDMyIGJ5dGUga2V5ISEhMTIzNDU2Nzg=");
        config.put("APP_LICENSE_KEY", "");
        config.put("B2B_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);

        if (!Files.exists(envPath)) {
            log.warn("El archivo .env no existe en la ruta: {}", envPath.toAbsolutePath());
            return config;
        }

        List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                String key = trimmed.substring(0, eqIdx).trim();
                String val = trimmed.substring(eqIdx + 1).trim();
                config.put(key, val);
            }
        }

        return config;
    }

    /**
     * Autentica la contraseña del administrador B2B.
     */
    public boolean authenticate(String password) {
        try {
            Map<String, String> config = readConfig();
            String storedPassword = config.getOrDefault("B2B_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD);
            return storedPassword.equals(password);
        } catch (IOException e) {
            log.error("Error al leer la contraseña del .env: {}", e.getMessage());
            return DEFAULT_ADMIN_PASSWORD.equals(password);
        }
    }

    /**
     * Guarda los nuevos parámetros de configuración en el archivo .env,
     * realizando comprobaciones en caliente para licencias y contraseñas.
     */
    public synchronized void updateConfig(Map<String, String> newParams, String currentPassword, String newAdminPassword) throws Exception {
        // 1. Validar la contraseña de administrador actual
        if (!authenticate(currentPassword)) {
            throw new IllegalArgumentException("La contraseña de administrador actual es incorrecta");
        }

        // 2. Si se cambia la clave de licencia, validarla en caliente ANTES de guardar
        String newLicense = newParams.get("APP_LICENSE_KEY");
        if (newLicense != null && !newLicense.trim().isEmpty()) {
            try {
                // Esto actualizará las cuotas y datos en memoria inmediatamente
                licenseValidatorService.validateAndApplyLicense(newLicense.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("La nueva clave de licencia no es válida: " + e.getMessage());
            }
        }

        // 3. Leer y reescribir el .env conservando comentarios y formato
        Path envPath = getEnvFilePath();
        List<String> outputLines = new ArrayList<>();
        Map<String, String> keysToUpdate = new HashMap<>(newParams);

        // Si se especificó cambio de contraseña de administración
        if (newAdminPassword != null && !newAdminPassword.trim().isEmpty()) {
            keysToUpdate.put("B2B_ADMIN_PASSWORD", newAdminPassword.trim());
        }

        if (Files.exists(envPath)) {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    outputLines.add(line);
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    if (keysToUpdate.containsKey(key)) {
                        String newVal = keysToUpdate.remove(key);
                        // Mantener la indentación o espaciado original
                        outputLines.add(key + "=" + newVal);
                    } else {
                        outputLines.add(line);
                    }
                } else {
                    outputLines.add(line);
                }
            }
        }

        // Agregar las claves que no existían originalmente en el archivo
        for (Map.Entry<String, String> entry : keysToUpdate.entrySet()) {
            outputLines.add(entry.getKey() + "=" + entry.getValue());
        }

        // 4. Escribir los cambios físicamente
        Files.write(envPath, outputLines, StandardCharsets.UTF_8);
        log.info("Archivo de configuración .env actualizado exitosamente.");
    }
}
