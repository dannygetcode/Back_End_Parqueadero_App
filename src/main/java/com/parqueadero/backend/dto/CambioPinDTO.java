package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambioPinDTO(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String pinActual,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String pinNuevo) {

    @Override
    public String toString() {
        return "CambioPinDTO[***]";
    }
}
