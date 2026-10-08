package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.EstadoPago;
import com.parqueadero.backend.entity.OcrEstado;
import com.parqueadero.backend.entity.RegistradoPor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PagoDTO(
        Long id,
        Long usuarioId,
        String usuarioNombre,
        String placa,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        Integer montoEsperado,
        Integer montoOcr,
        LocalDate fechaPagoOcr,
        OcrEstado ocrEstado,
        Integer montoConfirmado,
        EstadoPago estado,
        String motivoRechazo,
        String observacion,
        RegistradoPor registradoPor,
        boolean posibleDuplicado,
        String comprobanteUrl,
        OffsetDateTime creadoEn,
        OffsetDateTime revisadoEn) {
}
