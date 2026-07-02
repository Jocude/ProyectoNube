package com.cloudstorage.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios para {@link EncryptionService}.
 * Verifica el ciclo cifrado/descifrado con AES-256-GCM.
 */
@DisplayName("EncryptionService Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    // Clave AES-256 de 32 bytes codificada en Base64
    private static final String TEST_KEY = "dGhpcyBpcyBhIDMyIGJ5dGUga2V5ISEhMTIzNDU2Nzg=";

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        ReflectionTestUtils.setField(encryptionService, "encryptionKeyBase64", TEST_KEY);
        encryptionService.init();
    }

    @Test
    @DisplayName("Cifrar y descifrar un mensaje de texto debe producir el dato original")
    void encryptDecryptRoundTrip() {
        byte[] original = "Hola, mundo secreto! 🔐".getBytes();

        byte[] encrypted = encryptionService.encrypt(original);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Los datos cifrados no deben ser iguales al texto plano")
    void encryptedDataDiffersFromPlain() {
        byte[] original = "datos de prueba".getBytes();
        byte[] encrypted = encryptionService.encrypt(original);

        assertThat(encrypted).isNotEqualTo(original);
    }

    @Test
    @DisplayName("Cifrar dos veces el mismo dato produce resultados distintos (IV aleatorio)")
    void sameDataProducesDifferentCiphertext() {
        byte[] original = "texto repetido".getBytes();

        byte[] enc1 = encryptionService.encrypt(original);
        byte[] enc2 = encryptionService.encrypt(original);

        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    @DisplayName("Descifrar datos modificados debe lanzar excepción de integridad GCM")
    void tamperingWithEncryptedDataThrowsException() {
        byte[] original = "datos importantes".getBytes();
        byte[] encrypted = encryptionService.encrypt(original);

        // Modificar el último byte (altera el tag GCM)
        encrypted[encrypted.length - 1] ^= 0xFF;

        assertThatThrownBy(() -> encryptionService.decrypt(encrypted))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Cifrar un arreglo vacío no debe lanzar excepción")
    void encryptEmptyArray() {
        byte[] empty = new byte[0];
        byte[] encrypted = encryptionService.encrypt(empty);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEmpty();
    }

    @Test
    @DisplayName("Cifrar archivos grandes debe funcionar correctamente")
    void encryptLargeData() {
        byte[] largeData = new byte[10 * 1024 * 1024]; // 10 MB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] encrypted = encryptionService.encrypt(largeData);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(largeData);
    }
}
