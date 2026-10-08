package com.parqueadero.backend.dto;

import java.time.OffsetDateTime;

/** Estado de la puerta; {@code evento} solo viene en la respuesta de PUT /api/puerta. */
public record PuertaDTO(boolean abierta, OffsetDateTime abiertaHasta, EventoAccesoDTO ultimoEvento, EventoAccesoDTO evento) {
}
