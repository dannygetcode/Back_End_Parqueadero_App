package com.parqueadero.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edición parcial: los campos nulos no se tocan. */
public record UsuarioActualizacionDTO(
        @Pattern(regexp = "^3\\d{9}$", message = "debe ser un celular colombiano de 10 dígitos que empieza por 3") String telefono,
        @Size(min = 1, max = 50, message = "de 1 a 50 caracteres") String nombre,
        @Size(min = 1, max = 50, message = "de 1 a 50 caracteres") String apellido,
        Long cupoId) {
}
