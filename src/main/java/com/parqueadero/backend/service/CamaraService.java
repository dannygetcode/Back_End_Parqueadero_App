package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CamaraDTO;
import com.parqueadero.backend.dto.CamaraUsuarioDTO;

import java.util.List;

public interface CamaraService {
    /** Vista del ADMIN, con url. */
    List<CamaraDTO> listarCamaras();

    /** Vista del USUARIO, sin url. */
    List<CamaraUsuarioDTO> listarCamarasParaUsuario();

    CamaraDTO actualizarEstado(Long id, boolean activa);

    CamaraDTO crearCamara(CamaraDTO dto);
}
