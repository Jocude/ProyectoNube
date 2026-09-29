package com.cloudstorage.api.service;

import com.cloudstorage.api.exception.StorageException;
import jakarta.annotation.PostConstruct;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de cifrado y descifrado de archivos utilizando AES-256-GCM.
 *
 * <p>Proporciona cifrado autenticado con datos asociados (AEAD) mediante el algoritmo AES en modo
 * Galois/Counter (GCM), garantizando tanto la confidencialidad como la integridad de los datos
 * cifrados.
 *
 * <p>El IV (Vector de Inicialización) de 12 bytes se genera aleatoriamente para cada operación de
 * cifrado y se antepone al texto cifrado resultante, permitiendo su extracción durante el
 * descifrado.
 *
 * <p>{@link SecureRandom} se reutiliza como campo de clase para mejorar el rendimiento, ya que la
 * instanciación repetida en cada llamada es innecesariamente costosa.
 *
 * @author Cloud Storage API
 * @version 1.0
 */
@Slf4j
@Service
public class EncryptionService {

    /** Longitud del IV para GCM en bytes */
    private static final int GCM_IV_LENGTH = 12;

    /** Longitud de la etiqueta de autenticación GCM en bits */
    private static final int GCM_TAG_LENGTH = 128;

    /** Algoritmo de cifrado utilizado */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    @Value("${app.encryption.key}")
    private String encryptionKeyBase64;

    private SecretKey secretKey;

    /**
     * Generador de números aleatorios seguro reutilizable. Declarado como campo de clase para
     * evitar la costosa re-instanciación en cada cifrado.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /** Longitud exigida de la clave AES-256 en bytes. */
    private static final int KEY_LENGTH_BYTES = 32;

    /**
     * Inicializa la clave secreta AES a partir de la clave codificada en Base64.
     *
     * @throws IllegalStateException si la clave falta, no es Base64 válido o no mide 32 bytes
     */
    @PostConstruct
    public void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            throw new IllegalStateException(
                    "Falta la clave de cifrado: configure APP_ENCRYPTION_KEY (ENCRYPTION_KEY en .env)");
        }
        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(encryptionKeyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La clave de cifrado no es Base64 válido", e);
        }
        if (decodedKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "La clave de cifrado debe medir "
                            + KEY_LENGTH_BYTES
                            + " bytes (AES-256), mide "
                            + decodedKey.length);
        }
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
        log.info("Servicio de cifrado inicializado correctamente con AES-256-GCM");
    }

    /**
     * Cifra un arreglo de bytes utilizando AES-256-GCM.
     *
     * <p>El resultado contiene el IV (12 bytes) seguido del texto cifrado con la etiqueta de
     * autenticación GCM adjunta.
     *
     * @param data los datos en texto plano a cifrar
     * @return arreglo de bytes que contiene IV + texto cifrado + etiqueta GCM
     * @throws StorageException si ocurre un error durante el proceso de cifrado
     */
    public byte[] encrypt(byte[] data) {
        try {
            // Generar IV aleatorio de 12 bytes usando el SecureRandom de instancia
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Configurar parámetros GCM
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // Inicializar cifrador en modo ENCRYPT
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);

            // Cifrar los datos
            byte[] encryptedData = cipher.doFinal(data);

            // Combinar IV + datos cifrados
            byte[] combined = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, combined, GCM_IV_LENGTH, encryptedData.length);

            log.debug(
                    "Archivo cifrado exitosamente. Tamaño original: {} bytes, tamaño cifrado: {} bytes",
                    data.length,
                    combined.length);

            return combined;
        } catch (Exception e) {
            log.error("Error al cifrar el archivo: {}", e.getMessage(), e);
            throw new StorageException("Error al cifrar el archivo");
        }
    }

    /**
     * Cifra un flujo de datos con AES-256-GCM escribiendo el resultado en otro flujo, por bloques,
     * sin cargar el archivo completo en memoria.
     *
     * <p>El formato de salida es idéntico al de {@link #encrypt(byte[])} (IV + texto cifrado +
     * etiqueta GCM), por lo que se descifra con {@link #decrypt(byte[])}. No cierra los flujos.
     *
     * @param in datos en texto plano
     * @param out destino de los datos cifrados
     * @throws IOException si falla la lectura o la escritura
     * @throws StorageException si falla la inicialización del cifrador
     */
    public void encrypt(InputStream in, OutputStream out) throws IOException {
        Cipher cipher;
        byte[] iv = new byte[GCM_IV_LENGTH];
        try {
            secureRandom.nextBytes(iv);
            cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        } catch (GeneralSecurityException e) {
            log.error("Error al inicializar el cifrador: {}", e.getMessage(), e);
            throw new StorageException("Error al cifrar el archivo");
        }
        out.write(iv);
        // CipherOutputStream cifra cada bloque al escribirlo; al cerrarlo añade la etiqueta GCM.
        // Se envuelve 'out' para que cerrar el CipherOutputStream no cierre el flujo del llamador.
        try (OutputStream cipherOut =
                new CipherOutputStream(
                        new FilterOutputStream(out) {
                            @Override
                            public void write(byte[] b, int off, int len) throws IOException {
                                out.write(b, off, len);
                            }

                            @Override
                            public void close() throws IOException {
                                flush();
                            }
                        },
                        cipher)) {
            in.transferTo(cipherOut);
        }
    }

    /**
     * Descifra un arreglo de bytes previamente cifrado con AES-256-GCM.
     *
     * <p>Espera que los primeros 12 bytes del arreglo de entrada sean el IV, seguidos del texto
     * cifrado con la etiqueta GCM.
     *
     * @param encryptedData los datos cifrados (IV + texto cifrado + etiqueta GCM)
     * @return los datos descifrados en texto plano
     * @throws StorageException si ocurre un error durante el proceso de descifrado
     */
    public byte[] decrypt(byte[] encryptedData) {
        try {
            // Extraer IV (primeros 12 bytes)
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);

            // Extraer texto cifrado (resto de bytes)
            int ciphertextLength = encryptedData.length - GCM_IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertextLength);

            // Configurar parámetros GCM
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // Inicializar cifrador en modo DECRYPT
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            // Descifrar los datos
            byte[] decryptedData = cipher.doFinal(ciphertext);

            log.debug(
                    "Archivo descifrado exitosamente. Tamaño cifrado: {} bytes, tamaño descifrado: {} bytes",
                    encryptedData.length,
                    decryptedData.length);

            return decryptedData;
        } catch (Exception e) {
            log.error("Error al descifrar el archivo: {}", e.getMessage(), e);
            throw new StorageException("Error al descifrar el archivo");
        }
    }
}
