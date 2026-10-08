package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;

public record CupoResumenDTO(Long id, String codigo, TipoVehiculo tipoVehiculo) {
}
