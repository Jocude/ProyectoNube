package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.AuthResponse;
import com.cloudstorage.api.dto.LoginRequest;
import com.cloudstorage.api.dto.RegisterRequest;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.exception.ConflictException;
import com.cloudstorage.api.exception.RegistrationDisabledException;
import com.cloudstorage.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Servicio de autenticación que gestiona el registro e inicio de sesión de usuarios.
 * <p>
 * Proporciona la lógica de negocio para registrar nuevos usuarios con
 * contraseñas cifradas y autenticar usuarios existentes, generando
 * tokens JWT en ambos casos. Incluye protección contra fuerza bruta
 * bloqueando la cuenta temporalmente tras múltiples intentos fallidos.
 * </p>
 *
 * @author CloudStorage API
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Número de intentos fallidos antes de bloquear la cuenta */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** Duración del bloqueo en minutos tras superar los intentos máximos */
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final String INVALID_CREDENTIALS = "Credenciales inválidas";

    /** Hash BCrypt de relleno para igualar tiempos de login (se calcula una vez, al usarlo). */
    private volatile String dummyPasswordHash;

    /**
     * Si es {@code false}, solo se permite registrar al primer usuario (el propietario del
     * servidor); el resto de registros se rechazan. Configurable con APP_REGISTRATION_ENABLED.
     */
    @Value("${app.registration.enabled:true}")
    private boolean registrationEnabled = true;

    /**
     * Registra un nuevo usuario en el sistema.
     * <p>
     * Verifica que el email no esté registrado previamente, cifra la contraseña,
     * persiste el usuario y genera un token JWT para autenticación inmediata.
     * </p>
     *
     * @param request los datos de registro del nuevo usuario
     * @return la respuesta de autenticación con el token JWT y datos del usuario
     * @throws RuntimeException si el email ya está registrado en el sistema
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!registrationEnabled && userRepository.count() > 0) {
            log.warn("Registro rechazado (registro deshabilitado): {}", request.getEmail());
            throw new RegistrationDisabledException(
                    "El registro de nuevos usuarios está deshabilitado");
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("El email ya está registrado");
        }

        User user = User.builder()
                .name(request.getName())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        log.info("Usuario registrado exitosamente: {}", savedUser.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .build();
    }

    /**
     * Autentica un usuario existente en el sistema.
     * <p>
     * Busca el usuario por email, verifica que la contraseña proporcionada
     * coincida con la almacenada y genera un token JWT si las credenciales
     * son válidas. Implementa protección contra fuerza bruta bloqueando
     * la cuenta temporalmente tras {@value #MAX_FAILED_ATTEMPTS} intentos fallidos.
     * </p>
     *
     * @param request los datos de inicio de sesión (email y contraseña)
     * @return la respuesta de autenticación con el token JWT y datos del usuario
     * @throws RuntimeException si las credenciales son inválidas
     * @throws LockedException  si la cuenta está bloqueada por intentos fallidos
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(normalizeEmail(request.getEmail()));
        if (found.isEmpty()) {
            // Se calcula igualmente un hash BCrypt para que la respuesta tarde lo mismo exista o
            // no el email; si no, midiendo el tiempo se podría averiguar qué cuentas existen.
            passwordEncoder.matches(request.getPassword(), dummyPasswordHash());
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        User user = found.get();

        // Verificar si la cuenta está bloqueada
        if (!user.isAccountNonLocked()) {
            log.warn("Intento de login en cuenta bloqueada: {}", user.getEmail());
            throw new LockedException(
                    "Cuenta bloqueada temporalmente por múltiples intentos fallidos. " +
                    "Intente de nuevo después de las "
                            + user.getLockedUntil().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        // Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        // Login exitoso: resetear contadores
        resetFailedAttempts(user);

        String token = jwtService.generateToken(user);
        log.info("Usuario autenticado exitosamente: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Registra un intento fallido de login y bloquea la cuenta si se supera el límite.
     *
     * @param user el usuario que intentó hacer login
     */
    private void handleFailedLogin(User user) {
        int newAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(newAttempts);

        if (newAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("Cuenta bloqueada por {} minutos: {} (intentos fallidos: {})",
                    LOCK_DURATION_MINUTES, user.getEmail(), newAttempts);
        } else {
            log.warn("Intento fallido de login para: {} ({}/{})",
                    user.getEmail(), newAttempts, MAX_FAILED_ATTEMPTS);
        }

        userRepository.save(user);
    }

    /**
     * Resetea los contadores de intentos fallidos y desbloquea la cuenta.
     *
     * @param user el usuario que hizo login exitosamente
     */
    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    /** Emails sin espacios y en minúsculas: "Ana@X.com" y "ana@x.com" son la misma cuenta. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("contraseña-de-relleno");
        }
        return dummyPasswordHash;
    }
}
