package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.PuertaDTO;

public interface PuertaService {
    PuertaDTO obtenerEstado();
    PuertaDTO actualizarEstado(Boolean nuevaEstado);
}
