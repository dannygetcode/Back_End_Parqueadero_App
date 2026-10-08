package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;

/**
 * ocupado: el cupo está asignado a un usuario no dado de baja, en cualquier estado. vigente: ese usuario está ACTIVO.
 * Es ocupación "comercial", no física (para la física, ver GET /api/accesos/ocupacion).
 */
public record CupoDTO(Long id, String codigo, TipoVehiculo tipoVehiculo, boolean activo, Long usuarioId, boolean ocupado,
                      boolean vigente) {
}
