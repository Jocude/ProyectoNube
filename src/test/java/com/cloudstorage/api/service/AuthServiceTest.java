package com.cloudstorage.api.service;

import com.cloudstorage.api.dto.AuthResponse;
import com.cloudstorage.api.dto.LoginRequest;
import com.cloudstorage.api.dto.RegisterRequest;
import com.cloudstorage.api.entity.User;
import com.cloudstorage.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para {@link AuthService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = new BCryptPasswordEncoder().encode(PASSWORD);

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Registro exitoso devuelve token y datos del usuario")
    void registerSuccess() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(jwtService.generateToken(any())).thenReturn("test-jwt-token");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder()
                    .id(UUID.randomUUID())
                    .name(u.getName())
                    .email(u.getEmail())
                    .password(u.getPassword())
                    .build();
        });

        RegisterRequest req = new RegisterRequest();
        req.setName("Test User");
        req.setEmail(EMAIL);
        req.setPassword(PASSWORD);

        AuthResponse response = authService.register(req);

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("Registro con email duplicado lanza RuntimeException")
    void registerDuplicateEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        RegisterRequest req = new RegisterRequest();
        req.setName("Test");
        req.setEmail(EMAIL);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ya está registrado");
    }

    @Test
    @DisplayName("Login exitoso con credenciales válidas")
    void loginSuccess() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Test User")
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .failedLoginAttempts(0)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("valid-token");
        when(userRepository.save(any())).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setEmail(EMAIL);
        req.setPassword(PASSWORD);

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("Login con contraseña incorrecta lanza RuntimeException")
    void loginInvalidPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .failedLoginAttempts(0)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setEmail(EMAIL);
        req.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credenciales inválidas");
    }

    @Test
    @DisplayName("Login en cuenta bloqueada lanza LockedException")
    void loginLockedAccount() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .failedLoginAttempts(5)
                .lockedUntil(LocalDateTime.now().plusMinutes(10))
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest();
        req.setEmail(EMAIL);
        req.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("Cinco intentos fallidos bloquean la cuenta")
    void fiveFailedAttemptsLockAccount() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(EMAIL)
                .password(ENCODED_PASSWORD)
                .failedLoginAttempts(4)
                .build();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginRequest req = new LoginRequest();
        req.setEmail(EMAIL);
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class);

        verify(userRepository).save(argThat(u -> u.getLockedUntil() != null));
    }
}
