package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CamaraDTO(
        Long id,
        @NotBlank(message = "es obligatorio") @Size(max = 50, message = "máximo 50 caracteres") String nombre,
        @Size(max = 200, message = "máximo 200 caracteres") @Pattern(regexp = "^https?://\\S+$", message = "debe ser una URL http o https") String url,
        Boolean activa,
        Boolean simulada) {
}
