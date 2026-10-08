package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoDTO(
        @NotNull(message = "es obligatoria") Accion accion,
        @Size(max = 200, message = "máximo 200 caracteres") String motivo) {

    public enum Accion { SUSPENDER, REACTIVAR }
}
