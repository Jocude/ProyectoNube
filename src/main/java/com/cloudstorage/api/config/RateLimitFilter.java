package com.cloudstorage.api.config;

import com.cloudstorage.api.exception.TooManyRequestsException;
import com.cloudstorage.api.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Filtro HTTP que aplica rate limiting a los endpoints de autenticación.
 *
 * <p>Intercepta todas las peticiones a {@code /api/auth/**} y verifica que
 * la IP origen no haya superado el límite de peticiones permitidas por minuto.</p>
 *
 * @author CloudStorage Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Aplicar rate limiting solo a endpoints de autenticación
        if (requestUri.startsWith("/api/auth/")) {
            String ipAddress = extractClientIp(request);

            try {
                rateLimitService.checkRateLimit(ipAddress);
            } catch (TooManyRequestsException ex) {
                sendRateLimitResponse(response, ex.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae la dirección IP real del cliente, teniendo en cuenta proxies inversos.
     *
     * @param request la petición HTTP
     * @return la dirección IP del cliente
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // El primer IP en la cadena es el cliente original
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Escribe una respuesta 429 Too Many Requests en formato JSON.
     *
     * @param response la respuesta HTTP
     * @param message  el mensaje de error
     */
    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
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
