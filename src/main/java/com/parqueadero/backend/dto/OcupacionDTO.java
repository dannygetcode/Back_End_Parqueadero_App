package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoVehiculo;

import java.time.OffsetDateTime;
import java.util.List;

public record OcupacionDTO(List<PorTipo> porTipo, List<VehiculoDentro> vehiculosDentro) {

    public record PorTipo(TipoVehiculo tipoVehiculo, long cupos, long ocupados, long libres) {
    }

    public record VehiculoDentro(String placa, String usuario, OffsetDateTime desde) {
    }
}
