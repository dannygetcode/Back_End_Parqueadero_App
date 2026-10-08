package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.TarifaDTO;
import com.parqueadero.backend.entity.Tarifa;
import com.parqueadero.backend.entity.TipoVehiculo;

import java.time.LocalDate;
import java.util.List;

public interface TarifaService {

    /** Con {@code vigentes} solo la vigente hoy por tipo; si no, el historial completo. */
    List<TarifaDTO> listar(boolean vigentes);

    TarifaDTO crear(TarifaDTO dto);

    /** Tarifa de mayor vigenteDesde <= fecha; 409 SIN_TARIFA_VIGENTE si no hay. */
    Tarifa vigenteEn(TipoVehiculo tipo, LocalDate fecha);
}
