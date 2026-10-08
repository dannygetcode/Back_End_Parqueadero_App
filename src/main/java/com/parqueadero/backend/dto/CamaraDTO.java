package com.parqueadero.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cámara tal como la ve el ADMIN (con {@code url}). El USUARIO recibe {@link CamaraUsuarioDTO}.
 * La URL no puede llevar credenciales (userinfo {@code usuario:clave@host}): se guardarían en claro y saldrían por la API.
 */
public record CamaraDTO(
        Long id,
        @NotBlank(message = "es obligatorio") @Size(max = 50, message = "máximo 50 caracteres") String nombre,
        @Size(max = 200, message = "máximo 200 caracteres")
        @Pattern(regexp = CamaraDTO.URL_SIN_CREDENCIALES,
                message = "debe ser una URL http o https sin usuario ni contraseña") String url,
        Boolean activa,
        Boolean simulada) {

    /** http(s)://host[:puerto][/ruta...] sin '@' en la autoridad (sin userinfo). */
    public static final String URL_SIN_CREDENCIALES = "^https?://[^\\s/?#@]+([/?#]\\S*)?$";
}
