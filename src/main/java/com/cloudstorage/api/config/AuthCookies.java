package com.cloudstorage.api.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de sesión que transporta el JWT del frontend web.
 *
 * <ul>
 *   <li>{@code HttpOnly}: JavaScript no puede leerla, así que un fallo XSS no puede robar el token.
 *   <li>{@code SameSite=Strict}: el navegador no la envía en peticiones iniciadas desde otras webs
 *       (protección CSRF, reforzada con la cabecera {@link #CSRF_HEADER}).
 *   <li>{@code Secure}: solo por HTTPS cuando la petición llega cifrada (p. ej. por el túnel).
 * </ul>
 *
 * <p>Los clientes de la API (Swagger, scripts) pueden seguir usando la cabecera {@code
 * Authorization: Bearer}.
 */
@Component
public class AuthCookies {

    public static final String NAME = "cs_session";

    /**
     * Cabecera obligatoria en peticiones que modifican datos autenticadas por cookie. Un formulario
     * de otra web no puede añadir cabeceras propias, así que no puede falsificar estas peticiones.
     */
    public static final String CSRF_HEADER = "X-Requested-With";

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /** Crea la cookie de sesión con el token. */
    public ResponseCookie create(String token, HttpServletRequest request) {
        return base(token, request).maxAge(Duration.ofMillis(jwtExpirationMs)).build();
    }

    /** Crea una cookie vacía y caducada que borra la sesión en el navegador. */
    public ResponseCookie clear(HttpServletRequest request) {
        return base("", request).maxAge(0).build();
    }

    /** Lee el token de la cookie de sesión, si la hay. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> NAME.equals(c.getName()) && !c.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    private static ResponseCookie.ResponseCookieBuilder base(
            String value, HttpServletRequest request) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(isHttps(request))
                .sameSite("Strict")
                .path("/");
    }

    /**
     * HTTPS directo o a través de un proxy (Cloudflare) que lo indica en X-Forwarded-Proto. Fiarse
     * de esa cabecera aquí es inocuo: falsificarla solo haría la cookie más restrictiva.
     */
    private static boolean isHttps(HttpServletRequest request) {
        return request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
