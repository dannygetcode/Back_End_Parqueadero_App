package com.parqueadero.backend.dto;

/**
 * Vista de una cámara para el USUARIO (N4): sin {@code url}. La URL de la cámara (que puede llevar la IP interna o la
 * ruta del stream) solo la ve el ADMIN en {@link CamaraDTO}.
 */
public record CamaraUsuarioDTO(Long id, String nombre, boolean activa) {
}
