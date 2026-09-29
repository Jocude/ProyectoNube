package com.cloudstorage.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de seguridad de Spring Security para la API.
 *
 * <p>Establece una configuración sin estado (stateless) basada en JWT, deshabilita CSRF (apropiado
 * para APIs REST), configura CORS con valores predeterminados y define los endpoints públicos y
 * protegidos.
 *
 * @author CloudStorage API
 * @version 1.0
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    /**
     * Configura la cadena de filtros de seguridad HTTP.
     *
     * <p>- CSRF deshabilitado (API stateless)<br>
     * - CORS habilitado con configuración predeterminada<br>
     * - Gestión de sesiones sin estado (STATELESS)<br>
     * - Endpoints públicos: /api/auth/**, /api/share/**, /api/info, /error, /actuator/health,
     * /swagger-ui/**, /v3/api-docs/**<br>
     * - Todos los demás endpoints requieren autenticación<br>
     * - Filtro de rate limiting antes del filtro JWT<br>
     *
     * @param http el objeto HttpSecurity para configurar
     * @return la cadena de filtros de seguridad configurada
     * @throws Exception si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/",
                                                "/index.html",
                                                "/css/**",
                                                "/js/**",
                                                "/favicon.ico")
                                        .permitAll()
                                        .requestMatchers("/api/auth/**")
                                        .permitAll()
                                        .requestMatchers("/api/info")
                                        .permitAll()
                                        // Solo la descarga por token es pública; crear, listar y
                                        // revocar exigen sesión
                                        .requestMatchers(HttpMethod.GET, "/api/share/*")
                                        .permitAll()
                                        .requestMatchers("/error")
                                        .permitAll()
                                        .requestMatchers("/actuator/health", "/actuator/info")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // Sin token válido: 401 (por defecto Spring respondería 403, que el frontend
                // no interpreta como "sesión caducada")
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Crea el bean del codificador de contraseñas BCrypt.
     *
     * @return una instancia de BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Crea el bean del gestor de autenticación.
     *
     * @param authenticationConfiguration la configuración de autenticación de Spring
     * @return el AuthenticationManager configurado
     * @throws Exception si ocurre un error al obtener el gestor de autenticación
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
