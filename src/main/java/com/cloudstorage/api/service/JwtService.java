package com.cloudstorage.api.service;

import com.cloudstorage.api.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Servicio para la gestión de tokens JWT.
 *
 * <p>Proporciona métodos para generar, validar y extraer información de tokens JWT utilizados en la
 * autenticación de la API.
 *
 * @author CloudStorage API
 * @version 1.0
 */
@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private SecretKey key;

    /** Longitud mínima del secreto JWT en bytes (256 bits, exigido por HS256). */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Inicializa la clave de firma HMAC-SHA a partir del secreto configurado.
     *
     * @throws IllegalStateException si el secreto falta o es demasiado corto; así la aplicación no
     *     arranca con una clave débil o conocida
     */
    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Falta el secreto JWT: configure APP_JWT_SECRET (JWT_SECRET en .env)");
        }
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "El secreto JWT debe tener al menos " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        log.info("Clave JWT inicializada correctamente");
    }

    /**
     * Genera un token JWT para el usuario autenticado.
     *
     * @param userDetails los detalles del usuario autenticado
     * @return el token JWT generado
     */
    public String generateToken(UserDetails userDetails) {
        User user = (User) userDetails;
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("userId", user.getId().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * Extrae el nombre de usuario (email) del token JWT.
     *
     * @param token el token JWT
     * @return el nombre de usuario (email) contenido en el token
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extrae el identificador único del usuario del token JWT.
     *
     * @param token el token JWT
     * @return el UUID del usuario contenido en el token
     */
    public UUID extractUserId(String token) {
        String userId = extractAllClaims(token).get("userId", String.class);
        return UUID.fromString(userId);
    }

    /**
     * Verifica si el token JWT es válido para el usuario proporcionado.
     *
     * <p>Un token es válido si el nombre de usuario coincide y no ha expirado.
     *
     * @param token el token JWT a validar
     * @param userDetails los detalles del usuario contra el cual validar
     * @return {@code true} si el token es válido, {@code false} en caso contrario
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Extrae todos los claims del token JWT.
     *
     * @param token el token JWT
     * @return los claims contenidos en el token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Verifica si el token JWT ha expirado.
     *
     * @param token el token JWT
     * @return {@code true} si el token ha expirado, {@code false} en caso contrario
     */
    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
