package com.cloudstorage.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal de la aplicación Cloud Storage API.
 *
 * <p>Punto de entrada de la aplicación Spring Boot que proporciona
 * una API REST para el almacenamiento seguro de archivos en la nube
 * con cifrado AES-256 y autenticación basada en JWT.</p>
 *
 * @author Cloud Storage Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class CloudStorageApplication {

    /**
     * Método principal que inicia la aplicación Spring Boot.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(CloudStorageApplication.class, args);
    }
}
