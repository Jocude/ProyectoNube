package com.cloudstorage.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * Servicio encargado de validar la clave de licencia comercial (License Key)
 * al arrancar el servidor. Si no existe o está vencida, detiene el sistema.
 *
 * <p>A diferencia de la versión anterior, todos los campos y métodos son de instancia,
 * permitiendo inyección limpia de dependencias y facilitando los tests unitarios.</p>
 */
@Slf4j
@Service
public class LicenseValidatorService implements CommandLineRunner {

    // Clave pública RSA del desarrollador (emparejada con la privada usada para firmar)
    private static final String PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmNDQHYgB4JJ/O2RwPZZJUhYy3sCDXahYq/ZOzc6xvJnvQ4VlrlYIACwCYwJHfMSXFkFd55gPnhRXgMNqWYMy+Qd7OBzeYV5sZwzGKlUvZfKp3TGhlfCITkkjTYGzgFNC8Jb67ym7+dNo48DEzPCCj/ZtbpLicYReAnCnAQlWD1LuQz6GyEkk3RmK1ZpKhlzbn1t3dLYly4UcRhvrOCi41HMZDTj/eC37dkmoecUvQeb9cMFRUic2MnGrC1l3QIAQJWHZUiA69lwTDbS/osP+1nsJdGIzHzihhvmsDzqFErlhpUE56yoEkyaj6jsecgfceYCAPdqEy1U8C3UDyQhw3QIDAQAB";

    @Value("${app.license-key:}")
    private String licenseKey;

    private volatile Long allowedQuotaBytes = 53687091200L; // 50 GB por defecto (fallback)
    private volatile String licensedTo = "Desconocido (Sin Licencia)";
    private volatile boolean licenseValid = false;

    /**
     * Devuelve la cuota de almacenamiento autorizada por la licencia activa.
     *
     * @return bytes de almacenamiento permitidos
     */
    public Long getAllowedQuota() {
        return allowedQuotaBytes;
    }

    /**
     * Devuelve el nombre del cliente al que está registrada la licencia activa.
     *
     * @return nombre del licenciatario
     */
    public String getLicensedTo() {
        return licensedTo;
    }

    /**
     * Indica si la licencia actual es válida.
     *
     * @return {@code true} si la licencia está activa y no ha expirado
     */
    public boolean isLicenseValid() {
        return licenseValid;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Verificando clave de licencia del software (Anti-Piratería)...");

        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            log.error("==========================================================================");
            log.error("[ERROR CRÍTICO] CLAVE DE LICENCIA NO ENCONTRADA.");
            log.error("Por favor, configure la variable 'APP_LICENSE_KEY' en el archivo .env.");
            log.error("El servidor se detendrá inmediatamente.");
            log.error("==========================================================================");
            System.exit(1);
            throw new RuntimeException("Fallo en el arranque: Licencia ausente");
        }

        try {
            validateAndApplyLicense(licenseKey);
        } catch (Exception e) {
            log.error("==========================================================================");
            log.error("[ERROR CRÍTICO] LA CLAVE DE LICENCIA NO ES VÁLIDA O HA EXPIRADO.");
            log.error("Detalle del fallo: {}", e.getMessage());
            log.error("El servidor se detendrá inmediatamente.");
            log.error("==========================================================================");
            System.exit(1);
            throw new RuntimeException("Fallo en el arranque: Licencia inválida o expirada", e);
        }
    }

    /**
     * Valida y aplica una clave de licencia JWT/RSA en caliente (en memoria).
     *
     * @param key la clave de licencia a verificar
     * @throws Exception si la firma es incorrecta, el token expiró o hay errores de parseo
     */
    public synchronized void validateAndApplyLicense(String key) throws Exception {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("La clave de licencia no puede estar vacía");
        }

        // 1. Reconstruir la clave pública RSA
        byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_PEM);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(spec);

        // 2. Parsear y verificar la firma del JWT usando la clave pública
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(key.trim())
                .getPayload();

        // 3. Extraer metadatos del cliente
        String tempLicensedTo = claims.getSubject();
        Date expiration = claims.getExpiration();

        Long quota = claims.get("quotaBytes", Long.class);
        long tempAllowedQuotaBytes = (quota != null) ? quota : 53687091200L;

        // 4. Comprobar fecha de expiración
        if (expiration.before(new Date())) {
            throw new Exception("La licencia expiró el " + new SimpleDateFormat("dd/MM/yyyy").format(expiration));
        }

        // Aplicar cambios en memoria
        this.licensedTo = tempLicensedTo;
        this.allowedQuotaBytes = tempAllowedQuotaBytes;
        this.licenseValid = true;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        log.info("==========================================================================");
        log.info(" ¡LICENCIA COMERCIAL APLICADA CORRECTAMENTE!");
        log.info(" Registrada a nombre de: {}", licensedTo);
        log.info(" Fecha de Expiración:   {}", sdf.format(expiration));
        log.info(" Límite de Almacenamiento Autorizado: {}", formatSize(allowedQuotaBytes));
        log.info("==========================================================================");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %cBiB", bytes / Math.pow(1024, exp), pre);
    }
}
