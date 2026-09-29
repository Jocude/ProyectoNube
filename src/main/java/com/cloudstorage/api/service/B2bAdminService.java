package com.cloudstorage.api.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio del panel de administración B2B.
 *
 * <p>La contraseña de administración se lee de {@code B2B_ADMIN_PASSWORD} en el archivo .env. Si no
 * está definida, el panel queda deshabilitado (no existe contraseña por defecto).
 *
 * <p>Por seguridad, el panel solo expone valores no secretos (rutas y licencia) y solo permite
 * cambiar la licencia y la propia contraseña de administración. Los secretos ({@code JWT_SECRET},
 * {@code ENCRYPTION_KEY}, {@code DB_PASSWORD}) nunca se leen ni se escriben desde la web: cambiar
 * la clave de cifrado, por ejemplo, dejaría ilegibles todos los archivos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class B2bAdminService {

    private static final String ADMIN_PASSWORD_KEY = "B2B_ADMIN_PASSWORD";
    private static final String LICENSE_KEY = "APP_LICENSE_KEY";

    /** Claves del .env que el panel puede mostrar (ninguna es secreta). */
    private static final List<String> VISIBLE_KEYS =
            List.of("HOST_STORAGE_PATH", "HOST_DB_PATH", LICENSE_KEY);

    /** Caracteres de un JWT (Base64URL y puntos). Impide inyectar saltos de línea en el .env. */
    private static final Pattern LICENSE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    /**
     * Contraseña de admin: 12-128 caracteres ASCII visibles, sin espacios ni caracteres que el .env
     * o Docker Compose interpretan de forma especial ({@code # $ " ' \ `}).
     */
    private static final Pattern ADMIN_PASSWORD_PATTERN =
            Pattern.compile("^[A-Za-z0-9!%&()*+,\\-./:;<=>?@\\[\\]^_{|}~]{12,128}$");

    private final LicenseValidatorService licenseValidatorService;

    /**
     * Resuelve la ruta del archivo .env activo (montado en el contenedor o local en desarrollo).
     */
    private Path getEnvFilePath() {
        Path envInContainer = Path.of("/app/.env");
        if (Files.exists(envInContainer)) {
            return envInContainer;
        }
        return Path.of(".env");
    }

    /** Lee las variables del .env tal cual, sin valores por defecto. */
    private Map<String, String> readEnv() throws IOException {
        Path envPath = getEnvFilePath();
        Map<String, String> env = new LinkedHashMap<>();
        if (!Files.exists(envPath)) {
            log.warn("El archivo .env no existe en la ruta: {}", envPath.toAbsolutePath());
            return env;
        }
        for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                env.put(trimmed.substring(0, eqIdx).trim(), trimmed.substring(eqIdx + 1).trim());
            }
        }
        return env;
    }

    /**
     * Devuelve la configuración visible en el panel (sin secretos).
     *
     * @return mapa con las rutas del host y la licencia actual
     * @throws IOException si no se puede leer el .env
     */
    public Map<String, String> readPublicConfig() throws IOException {
        Map<String, String> env = readEnv();
        Map<String, String> visible = new LinkedHashMap<>();
        for (String key : VISIBLE_KEYS) {
            visible.put(key, env.getOrDefault(key, ""));
        }
        return visible;
    }

    /**
     * Comprueba la contraseña de administración en tiempo constante.
     *
     * @param password contraseña recibida
     * @return {@code true} si coincide; {@code false} si no coincide o el panel está deshabilitado
     */
    public boolean authenticate(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        String storedPassword;
        try {
            storedPassword = readEnv().get(ADMIN_PASSWORD_KEY);
        } catch (IOException e) {
            log.error("Error al leer la contraseña de administración del .env: {}", e.getMessage());
            return false;
        }
        if (storedPassword == null || storedPassword.isEmpty()) {
            log.warn("Panel B2B deshabilitado: {} no está definida en el .env", ADMIN_PASSWORD_KEY);
            return false;
        }
        // MessageDigest.isEqual compara en tiempo constante: no revela por el tiempo de
        // respuesta cuántos caracteres iniciales son correctos.
        return MessageDigest.isEqual(
                storedPassword.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Actualiza la licencia y/o la contraseña de administración en el .env. El llamador debe haber
     * autenticado antes al administrador.
     *
     * @param newLicenseKey nueva licencia (se valida y aplica en caliente), o vacío para no cambiar
     * @param newAdminPassword nueva contraseña de administración, o vacío para no cambiar
     * @throws IllegalArgumentException si algún valor no es válido
     * @throws IOException si no se puede escribir el .env
     */
    public synchronized void updateConfig(String newLicenseKey, String newAdminPassword)
            throws IOException {
        Map<String, String> keysToUpdate = new HashMap<>();

        if (newLicenseKey != null && !newLicenseKey.isBlank()) {
            String license = newLicenseKey.trim();
            if (!LICENSE_PATTERN.matcher(license).matches()) {
                throw new IllegalArgumentException(
                        "La clave de licencia tiene un formato inválido");
            }
            try {
                // Actualiza cuota y titular en memoria inmediatamente
                licenseValidatorService.validateAndApplyLicense(license);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "La nueva clave de licencia no es válida: " + e.getMessage());
            }
            keysToUpdate.put(LICENSE_KEY, license);
        }

        if (newAdminPassword != null && !newAdminPassword.isEmpty()) {
            if (!ADMIN_PASSWORD_PATTERN.matcher(newAdminPassword).matches()) {
                throw new IllegalArgumentException(
                        "La contraseña de administración debe tener entre 12 y 128 caracteres,"
                                + " sin espacios ni los caracteres # $ \" ' \\ `");
            }
            keysToUpdate.put(ADMIN_PASSWORD_KEY, newAdminPassword);
        }

        if (keysToUpdate.isEmpty()) {
            return;
        }
        writeEnv(keysToUpdate);
        log.info("Archivo .env actualizado desde el panel B2B: {}", keysToUpdate.keySet());
    }

    /** Reescribe el .env sustituyendo solo las claves indicadas y conservando el resto. */
    private void writeEnv(Map<String, String> keysToUpdate) throws IOException {
        Path envPath = getEnvFilePath();
        Map<String, String> pending = new HashMap<>(keysToUpdate);
        List<String> outputLines = new ArrayList<>();

        if (Files.exists(envPath)) {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                int eqIdx = line.indexOf('=');
                if (!trimmed.startsWith("#") && eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    if (pending.containsKey(key)) {
                        outputLines.add(key + "=" + pending.remove(key));
                        continue;
                    }
                }
                outputLines.add(line);
            }
        }
        pending.forEach((key, value) -> outputLines.add(key + "=" + value));

        // Se escribe en el mismo archivo (no con renombrado atómico) porque el .env es un
        // bind mount de un solo fichero y Docker no permite reemplazarlo.
        Files.write(envPath, outputLines, StandardCharsets.UTF_8);
    }
}
