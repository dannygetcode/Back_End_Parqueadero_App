package com.parqueadero.backend.dto;

import java.time.OffsetDateTime;

public record CodigoValidacionDTO(String codigo, OffsetDateTime expiraEn) {

    @Override
    public String toString() {
        return "CodigoValidacionDTO[codigo=***, expiraEn=" + expiraEn + "]";
    }
}
