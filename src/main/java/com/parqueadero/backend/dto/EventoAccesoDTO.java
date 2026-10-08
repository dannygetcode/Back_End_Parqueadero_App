package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.MotivoAcceso;
import com.parqueadero.backend.entity.OrigenAcceso;
import com.parqueadero.backend.entity.ResultadoAcceso;
import com.parqueadero.backend.entity.TipoEvento;

import java.time.OffsetDateTime;

public record EventoAccesoDTO(
        Long id,
        String placaLeida,
        TipoEvento tipo,
        boolean tipoInferido,
        ResultadoAcceso resultado,
        MotivoAcceso motivo,
        OrigenAcceso origen,
        String observacion,
        Long usuarioId,
        String usuarioNombre,
        VehiculoDTO vehiculo,
        OffsetDateTime ocurridoEn,
        boolean puertaAbierta,
        boolean duplicado) {

    public boolean permitido() {
        return resultado == ResultadoAcceso.PERMITIDO;
    }
}
