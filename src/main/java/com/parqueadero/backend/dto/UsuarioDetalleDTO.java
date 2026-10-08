package com.parqueadero.backend.dto;

import java.time.LocalDate;

public record UsuarioDetalleDTO(
        UsuarioDTO usuario,
        VehiculoDTO vehiculo,
        CupoResumenDTO cupo,
        PagoDTO ultimoPago,
        LocalDate vigenteHasta) {
}
