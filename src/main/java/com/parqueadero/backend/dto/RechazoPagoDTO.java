package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazoPagoDTO(
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 200, message = "de 5 a 200 caracteres") String motivo) {
}
