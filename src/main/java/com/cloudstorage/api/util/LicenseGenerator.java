package com.cloudstorage.api.util;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;

/**
 * Utilidad interna para el administrador/proveedor del software. Genera un par de claves RSA
 * (Pública y Privada) y permite crear licencias JWT firmadas asimétricamente para los clientes B2B.
 */
public class LicenseGenerator {

    public static void main(String[] args) throws Exception {
        System.out.println("=== GENERADOR DE CLAVES Y LICENCIAS CLOUDSTORAGE ===");

        // 1. Generar par de claves RSA de 2048 bits
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair pair = keyPairGen.generateKeyPair();
        PrivateKey privateKey = pair.getPrivate();
        PublicKey publicKey = pair.getPublic();

        // Convertir claves a formato Base64 para guardarlas cómodamente
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());

        System.out.println("\n[LLAVE PÚBLICA (Introduce esta en tu LicenseValidatorService.java)]");
        System.out.println(publicKeyBase64);

        System.out.println(
                "\n[LLAVE PRIVADA (¡MANTÉNLA SECRETA! La usas para firmar nuevas licencias)]");
        System.out.println(privateKeyBase64);

        // 2. Crear una licencia de prueba válida (vence en 1 año)
        String clientCompany = "Empresa de Prueba S.A.";
        long expirationMs = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000); // 1 año
        Date expiryDate = new Date(expirationMs);
        long quotaBytes = 53687091200L; // 50 GB

        String licenseKey =
                Jwts.builder()
                        .subject(clientCompany)
                        .issuer("CloudStorage B2B Provider")
                        .issuedAt(new Date())
                        .expiration(expiryDate)
                        .claim("quotaBytes", quotaBytes)
                        .signWith(privateKey, Jwts.SIG.RS256)
                        .compact();

        System.out.println(
                "\n[CLAVE DE LICENCIA DE PRUEBA GENERADA (Copia esta en tu archivo .env)]");
        System.out.println(licenseKey);

        // 3. Crear una licencia de prueba EXPIRADA (para validar que el sistema la rechaza)
        long expiredMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // Expiró ayer
        String expiredLicenseKey =
                Jwts.builder()
                        .subject("Cliente Expirado S.L.")
                        .issuer("CloudStorage B2B Provider")
                        .issuedAt(new Date(expiredMs - 100000))
                        .expiration(new Date(expiredMs))
                        .claim("quotaBytes", quotaBytes)
                        .signWith(privateKey, Jwts.SIG.RS256)
                        .compact();

        System.out.println(
                "\n[CLAVE DE LICENCIA EXPIRADA (Para probar fallos de arranque en tu .env)]");
        System.out.println(expiredLicenseKey);
        System.out.println("=====================================================");
    }
}
