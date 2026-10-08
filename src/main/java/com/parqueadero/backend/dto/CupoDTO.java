package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;

/**
 * ocupado: el cupo está asignado a un usuario no dado de baja que está ACTIVO, o VENCIDO pero todavía dentro de
 * los días de gracia de su último periodo aprobado. Es ocupación "comercial", no física (para la física, ver
 * GET /api/accesos/ocupacion).
 */
public record CupoDTO(Long id, String codigo, TipoVehiculo tipoVehiculo, boolean activo, Long usuarioId, boolean ocupado) {
}
