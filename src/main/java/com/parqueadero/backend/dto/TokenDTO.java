package com.parqueadero.backend.dto;

import java.time.OffsetDateTime;

/** Respuesta común de los dos logins. */
public record TokenDTO(String token, String rol, OffsetDateTime expiraEn) {

    @Override
    public String toString() {
        return "TokenDTO[rol=" + rol + ", expiraEn=" + expiraEn + "]";
    }
}
