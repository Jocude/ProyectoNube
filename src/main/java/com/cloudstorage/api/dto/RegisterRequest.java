package com.cloudstorage.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO para la solicitud de registro de un nuevo usuario. Incluye validaciones para nombre, correo
 * electrónico y contraseña.
 *
 * @author CloudStorage Team
 */
@Data
public class RegisterRequest {

    /** Nombre completo del usuario. Debe tener entre 2 y 100 caracteres. */
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    /** Correo electrónico del usuario. Debe ser un formato de email válido. */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    /** Contraseña del usuario. Debe tener entre 8 y 100 caracteres. */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String password;
}
