package com.cloudstorage.api.config;

import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.repository.UserRepository;
import com.cloudstorage.api.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de autenticación JWT que se ejecuta una vez por cada solicitud HTTP.
 *
 * <p>Intercepta las solicitudes entrantes, extrae el token JWT del encabezado Authorization, lo
 * valida y establece el contexto de seguridad de Spring si el token es válido.
 *
 * @author CloudStorage API
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthCookies authCookies;

    /** Métodos HTTP que no modifican datos y, por tanto, no necesitan protección CSRF. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Obtiene el JWT de la cabecera {@code Authorization: Bearer} (clientes de la API) o, si no la
     * hay, de la cookie de sesión (frontend web).
     *
     * <p>Con la cookie, las peticiones que modifican datos deben llevar además la cabecera {@link
     * AuthCookies#CSRF_HEADER}: el navegador envía las cookies automáticamente, pero una web ajena
     * no puede añadir cabeceras propias, así que no puede hacer peticiones en nombre del usuario.
     *
     * @return el token, o {@code null} si no hay ninguno utilizable
     */
    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String cookieToken = authCookies.read(request).orElse(null);
        if (cookieToken == null) {
            return null;
        }
        if (!SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(AuthCookies.CSRF_HEADER) == null) {
            log.warn(
                    "Petición {} {} con cookie de sesión pero sin cabecera {}: se ignora la sesión",
                    request.getMethod(),
                    request.getRequestURI(),
                    AuthCookies.CSRF_HEADER);
            return null;
        }
        return cookieToken;
    }

    /**
     * Procesa la solicitud HTTP para autenticación basada en JWT.
     *
     * <p>Extrae el token del encabezado Authorization, valida el token y configura el contexto de
     * seguridad de Spring si es válido. Si el token es inválido o está ausente, la solicitud
     * continúa sin autenticación, delegando el control a Spring Security.
     *
     * @param request la solicitud HTTP entrante
     * @param response la respuesta HTTP
     * @param filterChain la cadena de filtros para continuar el procesamiento
     * @throws ServletException si ocurre un error en el servlet
     * @throws IOException si ocurre un error de entrada/salida
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            final String jwt = resolveToken(request);
            if (jwt == null) {
                filterChain.doFilter(request, response);
                return;
            }

            final String username = jwtService.extractUsername(jwt);

            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<User> userOptional = userRepository.findByEmail(username);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();

                    if (jwtService.isTokenValid(jwt, user)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities());
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.debug("Usuario autenticado exitosamente: {}", username);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo autenticar el token JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
