package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CamaraEstadoDTO(@NotNull(message = "es obligatorio") Boolean activa) {
}
