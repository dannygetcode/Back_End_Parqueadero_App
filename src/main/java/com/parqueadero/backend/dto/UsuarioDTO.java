package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.EstadoUsuario;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Usuario hacia fuera. Nunca lleva PIN, hashes ni código de validación. */
public record UsuarioDTO(
        Long id,
        String telefono,
        String nombre,
        String apellido,
        EstadoUsuario estado,
        String suspendidoMotivo,
        boolean validado,
        OffsetDateTime bloqueadoHasta,
        CupoResumenDTO cupo,
        VehiculoDTO vehiculo,
        LocalDate vigenteHasta,
        OffsetDateTime creadoEn,
        OffsetDateTime dadoDeBajaEn) {
}
