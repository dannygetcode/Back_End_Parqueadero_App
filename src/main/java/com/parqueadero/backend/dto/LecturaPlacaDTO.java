package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoEvento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Lectura de placa del simulador de cámara (SISTEMA) o del panel (ADMIN). {@code tipo} solo lo puede enviar el
 * admin. Sin campo {@code simulado} en Fase 1: el generador de datos de la Fase 2 escribe directo en la BD.
 */
public record LecturaPlacaDTO(
        @NotBlank(message = "es obligatoria") @Size(max = 15, message = "máximo 15 caracteres") String placa,
        Long camaraId,
        OffsetDateTime ocurridoEn,
        TipoEvento tipo) {
}
