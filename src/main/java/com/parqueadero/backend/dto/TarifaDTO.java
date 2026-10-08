package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record TarifaDTO(
        Long id,
        @NotNull(message = "es obligatorio") TipoVehiculo tipoVehiculo,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer valorMensual,
        @NotNull(message = "es obligatoria") LocalDate vigenteDesde) {
}
