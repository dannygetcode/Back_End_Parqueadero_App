package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.EventoAccesoDTO;
import com.parqueadero.backend.dto.OcupacionDTO;
import com.parqueadero.backend.entity.OrigenAcceso;
import com.parqueadero.backend.entity.ResultadoAcceso;
import com.parqueadero.backend.entity.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Registro de accesos por placa (ADR 0004): un solo flujo para cámara, app y panel. */
public interface AccesoService {

    /**
     * Lectura o apertura a registrar.
     *
     * @param tipo       explícito (solo admin); null para inferirlo
     * @param ocurridoEn null para "ahora"; si viene, como mucho {@code accesos.desfase-maximo-minutos} en el pasado
     * @param forzar     apertura manual del admin: queda PERMITIDA aunque las reglas la denegarían (FORZADO_ADMIN)
     */
    record Lectura(String placa, OrigenAcceso origen, TipoEvento tipo, Instant ocurridoEn, Long camaraId,
                   String observacion, boolean forzar) {
    }

    EventoAccesoDTO registrar(Lectura lectura);

    Page<EventoAccesoDTO> listar(LocalDate desde, LocalDate hasta, String placa, ResultadoAcceso resultado,
                                 Pageable pageable);

    /** Eventos del usuario de los últimos 30 días. */
    List<EventoAccesoDTO> mios(Long usuarioId);

    OcupacionDTO ocupacion();
}
