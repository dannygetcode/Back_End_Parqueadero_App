package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CupoActualizacionDTO(@NotNull(message = "es obligatorio") Boolean activo) {
}
