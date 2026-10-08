package com.parqueadero.backend.service;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.EventoAccesoDTO;
import com.parqueadero.backend.dto.PuertaComandoDTO;
import com.parqueadero.backend.dto.PuertaDTO;
import com.parqueadero.backend.entity.*;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.EventoAccesoRepository;
import com.parqueadero.backend.repository.PuertaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PuertaServiceImpl implements PuertaService {

    private final PuertaRepository puertaRepo;
    private final EventoAccesoRepository eventoRepo;
    private final AccesoService accesoService;
    private final UsuarioService usuarioService;
    private final Mapeos mapeos;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PuertaDTO obtenerEstado(UsuarioAutenticado quien) {
        Puerta puerta = puerta();
        EventoAccesoDTO ultimo = eventoRepo.findFirstByParqueaderoIdAndSimuladoFalseOrderByOcurridoEnDescIdDesc(
                        Parqueadero.PRINCIPAL)
                .filter(ev -> quien.esAdmin()
                        || (ev.getUsuario() != null && ev.getUsuario().getId().equals(quien.id())))
                .map(ev -> mapeos.evento(ev, false))
                .orElse(null);
        return dto(puerta, ultimo, null);
    }

    @Override
    @Transactional
    public PuertaDTO comando(PuertaComandoDTO dto, UsuarioAutenticado quien) {
        if (!dto.abierta()) {
            if (!quien.esAdmin()) {
                throw NegocioException.prohibido("Solo el administrador puede cerrar la puerta");
            }
            Puerta puerta = puerta();
            Instant ahora = clock.instant();
            if (puerta.estaAbierta(ahora)) {
                puerta.setAbiertaHasta(ahora);
            }
            return dto(puerta, null, null);
        }

        EventoAccesoDTO evento;
        if (quien.esAdmin()) {
            if (dto.placa() == null || dto.placa().isBlank()) {
                throw NegocioException.invalido("La placa es obligatoria para abrir desde el panel");
            }
            evento = accesoService.registrar(new AccesoService.Lectura(dto.placa(), OrigenAcceso.MANUAL_ADMIN,
                    dto.tipo(), null, null, dto.observacion(), true));
        } else {
            // RF-09: se relee el usuario (baja o bloqueo -> 403). Un SUSPENDIDO pasa por las reglas de acceso, que
            // le niegan la ENTRADA (queda registrado) pero le permiten salir.
            Usuario u = usuarioService.exigirOperable(quien.id(), false);
            Vehiculo v = mapeos.vehiculoActivo(u);
            if (v == null) {
                throw NegocioException.conflicto("No tiene un vehículo activo registrado");
            }
            evento = accesoService.registrar(new AccesoService.Lectura(v.getPlaca(), OrigenAcceso.APP_USUARIO,
                    null, null, null, null, false));
        }
        return dto(puerta(), evento, evento);
    }

    private Puerta puerta() {
        return puertaRepo.findFirstByParqueaderoIdOrderByIdAsc(Parqueadero.PRINCIPAL)
                .orElseThrow(() -> new IllegalStateException("No hay puerta configurada"));
    }

    private PuertaDTO dto(Puerta p, EventoAccesoDTO ultimo, EventoAccesoDTO evento) {
        return new PuertaDTO(p.estaAbierta(clock.instant()), Fechas.local(p.getAbiertaHasta()), ultimo, evento);
    }
}
