package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CamaraDTO;

import java.util.List;

public interface CamaraService {
    List<CamaraDTO> listarCamaras();

    CamaraDTO actualizarEstado(Long id, boolean activa);

    CamaraDTO crearCamara(CamaraDTO dto);
}
