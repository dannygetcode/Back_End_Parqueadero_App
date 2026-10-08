package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AprobacionPagoDTO(
        @NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo") Integer montoConfirmado,
        @Size(max = 200, message = "máximo 200 caracteres") String observacion) {
}
