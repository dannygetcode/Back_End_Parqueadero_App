package com.parqueadero.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta por el admin: usuario, vehículo y cupo en una sola operación. */
public record UsuarioAltaDTO(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^3\\d{9}$", message = "debe ser un celular colombiano de 10 dígitos que empieza por 3") String telefono,
        @NotBlank(message = "es obligatorio") @Size(max = 50, message = "máximo 50 caracteres") String nombre,
        @NotBlank(message = "es obligatorio") @Size(max = 50, message = "máximo 50 caracteres") String apellido,
        @NotNull(message = "es obligatorio") Long cupoId,
        @NotNull(message = "es obligatorio") @Valid VehiculoDTO vehiculo) {
}
