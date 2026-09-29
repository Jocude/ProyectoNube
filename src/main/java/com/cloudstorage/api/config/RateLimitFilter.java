package com.cloudstorage.api.config;

import com.cloudstorage.api.exception.TooManyRequestsException;
import com.cloudstorage.api.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro HTTP que aplica rate limiting a los endpoints sensibles a fuerza bruta: autenticación
 * ({@code /api/auth/**}) y panel de administración ({@code /api/admin/**}).
 *
 * <p>Cada grupo tiene su propio contador por IP, para que el uso del panel no consuma los intentos
 * de login y viceversa.
 *
 * <p>La IP del cliente se toma de la conexión TCP. Las cabeceras {@code CF-Connecting-IP} o {@code
 * X-Forwarded-For} las puede inventar cualquiera, así que solo se usan si la petición llega desde
 * el proxy de confianza configurado ({@code app.rate-limit.trusted-proxy-host}, p. ej. el
 * contenedor {@code tunnel} de Cloudflare).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.trusted-proxy-host:}")
    private String trustedProxyHost;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String group = rateLimitGroup(request.getRequestURI());
        if (group != null) {
            try {
                rateLimitService.checkRateLimit(group + "|" + extractClientIp(request));
            } catch (TooManyRequestsException ex) {
                sendRateLimitResponse(response, ex.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Devuelve el grupo de rate limit de la ruta, o {@code null} si no está limitada. */
    private static String rateLimitGroup(String uri) {
        if (uri.startsWith("/api/auth/")) {
            return "auth";
        }
        if (uri.startsWith("/api/admin/")) {
            return "admin";
        }
        return null;
    }

    /**
     * Obtiene la IP real del cliente. Solo confía en las cabeceras del proxy si la conexión viene
     * del proxy de confianza.
     */
    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        // Cloudflare envía la IP del visitante en CF-Connecting-IP
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (trustedProxyHost == null || trustedProxyHost.isBlank()) {
            return false;
        }
        try {
            // La JVM cachea la resolución DNS, así que no hay consulta por cada petición
            for (InetAddress address : InetAddress.getAllByName(trustedProxyHost)) {
                if (address.getHostAddress().equals(remoteAddr)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            log.debug("No se pudo resolver el proxy de confianza '{}'", trustedProxyHost);
        }
        return false;
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", 429);
        body.put("timestamp", LocalDateTime.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
