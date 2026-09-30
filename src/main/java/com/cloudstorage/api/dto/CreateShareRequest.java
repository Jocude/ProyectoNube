package com.cloudstorage.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** DTO para crear un enlace de compartición. */
@Data
public class CreateShareRequest {

    /** Horas de validez del enlace (por defecto, 24). */
    @Min(value = 1, message = "La expiración mínima es de 1 hora")
    @Max(value = 720, message = "La expiración máxima es de 720 horas (30 días)")
    private int expirationHours = 24;

    /** Número máximo de descargas, o {@code null} para ilimitadas. */
    @Min(value = 1, message = "El número máximo de descargas debe ser al menos 1")
    private Integer maxDownloads;
}
