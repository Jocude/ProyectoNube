package com.cloudstorage.api.controller;

import com.cloudstorage.api.config.AuthCookies;
import com.cloudstorage.api.dto.AuthResponse;
import com.cloudstorage.api.dto.LoginRequest;
import com.cloudstorage.api.dto.RegisterRequest;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para las operaciones de autenticación.
 *
 * <p>Expone los endpoints para el registro de nuevos usuarios y el inicio de sesión de usuarios
 * existentes. Todos los endpoints son públicos (no requieren autenticación previa).
 *
 * @author CloudStorage API
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * <p>Recibe los datos de registro, crea el usuario y devuelve un token JWT junto con los datos
     * del usuario creado. El token se envía también en una cookie HttpOnly para el frontend web. El
     * primer usuario registrado es el administrador del servidor.
     *
     * @param request los datos de registro del nuevo usuario (nombre, email, contraseña)
     * @return ResponseEntity con la respuesta de autenticación y estado HTTP 201 (CREATED)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("Solicitud de registro recibida para email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookies.create(response.getToken(), httpRequest).toString())
                .body(response);
    }

    /**
     * Autentica un usuario existente en el sistema.
     *
     * <p>Recibe las credenciales del usuario, las valida y devuelve un token JWT junto con los
     * datos del usuario autenticado.
     *
     * @param request los datos de inicio de sesión (email y contraseña)
     * @return ResponseEntity con la respuesta de autenticación y estado HTTP 200 (OK)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Solicitud de login recibida para email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookies.create(response.getToken(), httpRequest).toString())
                .body(response);
    }

    /**
     * Devuelve los datos de la sesión actual. El frontend lo usa al cargar la página para saber si
     * la cookie de sesión sigue siendo válida (no puede leerla: es HttpOnly).
     *
     * @param currentUser el usuario autenticado
     * @return nombre, email y rol (sin token)
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(authService.currentUser(currentUser));
    }

    /**
     * Cierra la sesión borrando la cookie. El JWT sigue siendo válido hasta que caduca si alguien
     * lo hubiera copiado, pero el navegador deja de enviarlo.
     *
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.clear(httpRequest).toString())
                .build();
    }
}
