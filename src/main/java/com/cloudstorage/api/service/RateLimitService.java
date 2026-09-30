package com.cloudstorage.api.service;

import com.cloudstorage.api.exception.TooManyRequestsException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Servicio de limitación de tasa de peticiones (rate limiting) por IP.
 *
 * <p>Utiliza una ventana deslizante simple en memoria para limitar las peticiones por dirección IP.
 * Diseñado principalmente para proteger los endpoints de autenticación contra ataques de fuerza
 * bruta y abuso automatizado.
 *
 * <p>Límites configurados:
 *
 * <ul>
 *   <li>Máximo de {@value #MAX_REQUESTS} peticiones por ventana de {@value #WINDOW_SECONDS}
 *       segundos
 * </ul>
 *
 * @author CloudStorage Team
 */
@Slf4j
@Service
public class RateLimitService {

    /** Número máximo de peticiones permitidas por ventana de tiempo */
    private static final int MAX_REQUESTS = 15;

    /** Duración de la ventana de tiempo en segundos */
    private static final long WINDOW_SECONDS = 60;

    /** Registro de intentos por IP: almacena el conteo y el inicio de la ventana actual. */
    private final ConcurrentHashMap<String, IpBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Verifica y registra una petición para la IP dada. Lanza {@link TooManyRequestsException} si
     * se supera el límite.
     *
     * @param ipAddress la dirección IP del cliente
     * @throws TooManyRequestsException si la IP ha superado el límite de peticiones
     */
    public void checkRateLimit(String ipAddress) {
        buckets.compute(
                ipAddress,
                (ip, bucket) -> {
                    long now = Instant.now().getEpochSecond();

                    if (bucket == null || now - bucket.windowStart >= WINDOW_SECONDS) {
                        // Ventana nueva o expirada: reiniciar contador
                        return new IpBucket(now, new AtomicInteger(1));
                    }

                    int count = bucket.count.incrementAndGet();
                    if (count > MAX_REQUESTS) {
                        log.warn(
                                "Rate limit superado para IP: {} ({} peticiones en {} segundos)",
                                ip,
                                count,
                                WINDOW_SECONDS);
                        throw new TooManyRequestsException(
                                "Demasiadas peticiones. Por favor, espere "
                                        + WINDOW_SECONDS
                                        + " segundos antes de intentarlo de nuevo.");
                    }

                    return bucket;
                });
    }

    /**
     * Limpia las entradas expiradas del mapa de buckets para liberar memoria. Se ejecuta
     * automáticamente cada 5 minutos.
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void cleanExpiredBuckets() {
        long now = Instant.now().getEpochSecond();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_SECONDS);
        log.debug(
                "Limpieza de rate-limit buckets completada. Entradas restantes: {}",
                buckets.size());
    }

    /** Contenedor interno que agrupa el conteo y el inicio de ventana de una IP. */
    private static class IpBucket {
        final long windowStart;
        final AtomicInteger count;

        IpBucket(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
