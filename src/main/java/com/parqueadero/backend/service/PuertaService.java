package com.parqueadero.backend.service;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.PuertaComandoDTO;
import com.parqueadero.backend.dto.PuertaDTO;

/** Interruptor de la puerta; las aperturas delegan en AccesoService (ADR 0004). */
public interface PuertaService {

    /** Para un USUARIO, ultimoEvento solo se incluye si es suyo (privacidad). */
    PuertaDTO obtenerEstado(UsuarioAutenticado quien);

    PuertaDTO comando(PuertaComandoDTO dto, UsuarioAutenticado quien);
}
