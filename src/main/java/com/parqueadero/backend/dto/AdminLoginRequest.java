package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login del administrador. */
public record AdminLoginRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 50, message = "máximo 50 caracteres") String username,
        @NotBlank(message = "es obligatoria") @Size(max = 200, message = "máximo 200 caracteres") String password) {

    @Override
    public String toString() {
        return "AdminLoginRequest[username=" + username + ", password=***]";
    }
}
