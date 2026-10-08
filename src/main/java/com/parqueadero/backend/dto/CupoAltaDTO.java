package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CupoAltaDTO(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^[A-Z0-9-]{1,10}$", message = "de 1 a 10 caracteres A-Z, 0-9 o guion") String codigo,
        @NotNull(message = "es obligatorio") TipoVehiculo tipoVehiculo) {
}
