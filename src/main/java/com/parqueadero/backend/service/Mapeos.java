package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.*;
import com.parqueadero.backend.entity.*;
import com.parqueadero.backend.repository.PagoRepository;
import com.parqueadero.backend.repository.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Entidad -> DTO. Único sitio donde se decide qué sale por la API (nunca PIN, hashes ni código). */
@Component
@RequiredArgsConstructor
public class Mapeos {

    private final VehiculoRepository vehiculoRepo;
    private final PagoRepository pagoRepo;

    public CupoResumenDTO cupo(Cupo c) {
        return c == null ? null : new CupoResumenDTO(c.getId(), c.getCodigo(), c.getTipoVehiculo());
    }

    public VehiculoDTO vehiculo(Vehiculo v) {
        return v == null ? null
                : new VehiculoDTO(v.getId(), v.getPlaca(), v.getTipoVehiculo(), v.getCarroceria(), v.getColor(), v.getMarca());
    }

    public Vehiculo vehiculoActivo(Usuario u) {
        return vehiculoRepo.findFirstByUsuarioIdAndActivoTrue(u.getId()).orElse(null);
    }

    /** Fin del último periodo aprobado (null si no hay). */
    public LocalDate vigenteHasta(Long usuarioId) {
        return pagoRepo.findFirstByUsuarioIdAndEstadoAndSimuladoFalseOrderByPeriodoFinDesc(usuarioId, EstadoPago.APROBADO)
                .map(Pago::getPeriodoFin)
                .orElse(null);
    }

    public UsuarioDTO usuario(Usuario u) {
        return new UsuarioDTO(
                u.getId(),
                u.getTelefono(),
                u.getNombre(),
                u.getApellido(),
                u.getEstado(),
                u.getSuspendidoMotivo(),
                u.isValidado(),
                Fechas.local(u.getBloqueadoHasta()),
                cupo(u.getCupo()),
                vehiculo(vehiculoActivo(u)),
                vigenteHasta(u.getId()),
                Fechas.local(u.getCreadoEn()),
                Fechas.local(u.getDadoDeBajaEn()));
    }

    public PagoDTO pago(Pago p) {
        Usuario u = p.getUsuario();
        Vehiculo v = vehiculoActivo(u);
        return new PagoDTO(
                p.getId(),
                u.getId(),
                u.getNombre() + " " + u.getApellido(),
                v != null ? v.getPlaca() : null,
                p.getPeriodoInicio(),
                p.getPeriodoFin(),
                p.getMontoEsperado(),
                p.getMontoOcr(),
                p.getFechaPagoOcr(),
                p.getOcrEstado(),
                p.getMontoConfirmado(),
                p.getEstado(),
                p.getMotivoRechazo(),
                p.getObservacion(),
                p.getRegistradoPor(),
                p.isPosibleDuplicado(),
                p.getComprobanteRuta() != null ? "/api/pagos/" + p.getId() + "/comprobante" : null,
                Fechas.local(p.getCreadoEn()),
                Fechas.local(p.getRevisadoEn()));
    }

    /** {@code puertaAbierta}: el evento abrió la puerta (fue PERMITIDO). */
    public EventoAccesoDTO evento(EventoAcceso e, boolean duplicado) {
        Usuario u = e.getUsuario();
        return new EventoAccesoDTO(
                e.getId(),
                e.getPlacaLeida(),
                e.getTipo(),
                e.isTipoInferido(),
                e.getResultado(),
                e.getMotivo(),
                e.getOrigen(),
                e.getObservacion(),
                u != null ? u.getId() : null,
                u != null ? u.getNombre() + " " + u.getApellido() : null,
                vehiculo(e.getVehiculo()),
                Fechas.local(e.getOcurridoEn()),
                e.getResultado() == ResultadoAcceso.PERMITIDO,
                duplicado);
    }

    public CamaraDTO camara(Camara c) {
        return new CamaraDTO(c.getId(), c.getNombre(), c.getUrl(), c.isActiva(), c.isSimulada());
    }

    public TarifaDTO tarifa(Tarifa t) {
        return new TarifaDTO(t.getId(), t.getTipoVehiculo(), t.getValorMensual(), t.getVigenteDesde());
    }
}
