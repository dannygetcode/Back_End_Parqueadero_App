package com.parqueadero.backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Activación de la cuenta por el titular: código entregado por el admin, PIN nuevo y consentimiento (Ley 1581). */
public record ActivacionDTO(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "formato inválido") String telefono,
        @NotBlank(message = "es obligatorio") @Size(max = 20, message = "máximo 20 caracteres") String codigo,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String pin,
        @NotNull(message = "es obligatorio") @AssertTrue(message = "debe aceptar el tratamiento de datos") Boolean aceptaTratamientoDatos,
        @NotBlank(message = "es obligatoria") @Size(max = 20, message = "máximo 20 caracteres") String versionConsentimiento) {

    @Override
    public String toString() {
        return "ActivacionDTO[telefono=" + telefono + ", codigo=***, pin=***]";
    }
}
