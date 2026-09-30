package com.cloudstorage.api.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para operaciones de autenticación (login y registro). Contiene el token JWT
 * generado junto con información básica del usuario.
 *
 * @author CloudStorage Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /** Token JWT para autenticación en solicitudes posteriores. */
    private String token;

    /** Identificador único del usuario autenticado. */
    private UUID userId;

    /** Nombre completo del usuario autenticado. */
    private String name;

    /** Correo electrónico del usuario autenticado. */
    private String email;

    /** Rol del usuario en el sistema (ROLE_USER, ROLE_ADMIN). */
    private String role;
}
