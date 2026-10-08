package com.parqueadero.backend.dto;

import com.parqueadero.backend.entity.TipoEvento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Interruptor de la puerta. USUARIO: solo {abierta:true} (la placa sale de su vehículo). ADMIN: {abierta:true, placa,
 * tipo?, observacion?} o {abierta:false} para cerrar.
 */
public record PuertaComandoDTO(
        @NotNull(message = "es obligatorio") Boolean abierta,
        @Size(max = 15, message = "máximo 15 caracteres") String placa,
        TipoEvento tipo,
        @Size(max = 200, message = "máximo 200 caracteres") String observacion) {
}
