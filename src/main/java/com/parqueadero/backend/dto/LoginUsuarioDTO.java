package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginUsuarioDTO(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "formato inválido") String telefono,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String pin) {

    @Override
    public String toString() {
        return "LoginUsuarioDTO[telefono=" + telefono + ", pin=***]";
    }
}
