package com.parqueadero.backend.dto;

import java.time.OffsetDateTime;

/** Única respuesta (con CodigoValidacionDTO) que lleva el código en claro: se muestra una sola vez. */
public record UsuarioAltaRespuestaDTO(UsuarioDTO usuario, String codigoValidacion, OffsetDateTime codigoExpiraEn) {

    @Override
    public String toString() {
        return "UsuarioAltaRespuestaDTO[usuario=" + (usuario != null ? usuario.id() : null) + ", codigo=***]";
    }
}
