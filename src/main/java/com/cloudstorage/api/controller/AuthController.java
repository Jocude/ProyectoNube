package com.cloudstorage.api.controller;

import com.cloudstorage.api.dto.AuthResponse;
import com.cloudstorage.api.dto.LoginRequest;
import com.cloudstorage.api.dto.RegisterRequest;
import com.cloudstorage.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para las operaciones de autenticación.
 * <p>
 * Expone los endpoints para el registro de nuevos usuarios y el
 * inicio de sesión de usuarios existentes. Todos los endpoints
 * son públicos (no requieren autenticación previa).
 * </p>
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

    /**
     * Registra un nuevo usuario en el sistema.
     * <p>
     * Recibe los datos de registro, crea el usuario y devuelve
     * un token JWT junto con los datos del usuario creado.
     * </p>
     *
     * @param request los datos de registro del nuevo usuario (nombre, email, contraseña)
     * @return ResponseEntity con la respuesta de autenticación y estado HTTP 201 (CREATED)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Solicitud de registro recibida para email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Autentica un usuario existente en el sistema.
     * <p>
     * Recibe las credenciales del usuario, las valida y devuelve
     * un token JWT junto con los datos del usuario autenticado.
     * </p>
     *
     * @param request los datos de inicio de sesión (email y contraseña)
     * @return ResponseEntity con la respuesta de autenticación y estado HTTP 200 (OK)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Solicitud de login recibida para email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
