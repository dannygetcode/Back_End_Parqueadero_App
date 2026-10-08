package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CupoActualizacionDTO;
import com.parqueadero.backend.dto.CupoAltaDTO;
import com.parqueadero.backend.dto.CupoDTO;

import java.util.List;

public interface CupoService {

    List<CupoDTO> listar();

    CupoDTO crear(CupoAltaDTO dto);

    CupoDTO actualizar(Long id, CupoActualizacionDTO dto);
}
