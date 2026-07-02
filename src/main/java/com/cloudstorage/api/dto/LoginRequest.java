package com.cloudstorage.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO para la solicitud de inicio de sesión.
 * Contiene las credenciales necesarias para autenticar a un usuario.
 *
 * @author CloudStorage Team
 */
@Data
public class LoginRequest {

    /**
     * Correo electrónico del usuario. Debe ser un formato de email válido.
     */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    /**
     * Contraseña del usuario en texto plano para verificación.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
