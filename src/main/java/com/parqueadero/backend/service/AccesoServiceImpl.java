package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.EventoAccesoDTO;
import com.parqueadero.backend.dto.OcupacionDTO;
import com.parqueadero.backend.entity.*;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccesoServiceImpl implements AccesoService {

    private final EventoAccesoRepository eventoRepo;
    private final VehiculoRepository vehiculoRepo;
    private final CamaraRepository camaraRepo;
    private final PuertaRepository puertaRepo;
    private final CupoRepository cupoRepo;
    private final ParqueaderoRepository parqueaderoRepo;
    private final Mapeos mapeos;
    private final Clock clock;
    private final Duration antirrebote;
    private final Duration apertura;
    private final Duration desfaseMaximo;

    public AccesoServiceImpl(EventoAccesoRepository eventoRepo, VehiculoRepository vehiculoRepo,
                             CamaraRepository camaraRepo, PuertaRepository puertaRepo, CupoRepository cupoRepo,
                             ParqueaderoRepository parqueaderoRepo, Mapeos mapeos, Clock clock,
                             @Value("${accesos.antirrebote-segundos:60}") long antirreboteSegundos,
                             @Value("${accesos.apertura-segundos:10}") long aperturaSegundos,
                             @Value("${accesos.desfase-maximo-minutos:5}") long desfaseMinutos) {
        this.eventoRepo = eventoRepo;
        this.vehiculoRepo = vehiculoRepo;
        this.camaraRepo = camaraRepo;
        this.puertaRepo = puertaRepo;
        this.cupoRepo = cupoRepo;
        this.parqueaderoRepo = parqueaderoRepo;
        this.mapeos = mapeos;
        this.clock = clock;
        this.antirrebote = Duration.ofSeconds(antirreboteSegundos);
        this.apertura = Duration.ofSeconds(aperturaSegundos);
        this.desfaseMaximo = Duration.ofMinutes(desfaseMinutos);
    }

    @Override
    @Transactional
    public EventoAccesoDTO registrar(Lectura lectura) {
        Instant ahora = clock.instant();
        Instant ocurrido = validarInstante(lectura.ocurridoEn(), ahora);
        String placa = Placas.normalizar(lectura.placa());
        if (placa.isEmpty() || placa.length() > 15) {
            throw NegocioException.invalido("Placa inválida");
        }
        Camara camara = null;
        if (lectura.camaraId() != null) {
            camara = camaraRepo.findById(lectura.camaraId())
                    .filter(Camara::isActiva)
                    .orElseThrow(() -> NegocioException.conflicto("La cámara no existe o está inactiva"));
        }
        // Bloqueo de la fila de la puerta: serializa lecturas simultáneas (anti-rebote sin carreras).
        Puerta puerta = puertaRepo.findFirstByParqueaderoIdOrderByIdAsc(Parqueadero.PRINCIPAL)
                .flatMap(p -> puertaRepo.findWithLockById(p.getId()))
                .orElseThrow(() -> new IllegalStateException("No hay puerta configurada"));

        EventoAcceso e = new EventoAcceso();
        e.setParqueadero(parqueaderoRepo.getReferenceById(Parqueadero.PRINCIPAL));
        e.setPlacaLeida(placa);
        e.setCamara(camara);
        e.setPuerta(puerta);
        e.setOrigen(lectura.origen());
        e.setOcurridoEn(ocurrido);
        e.setTipoInferido(lectura.tipo() == null);
        String observacion = limpiar(lectura.observacion());

        Vehiculo v = vehiculoRepo.findFirstByPlacaAndActivoTrueAndSimuladoFalse(placa).orElse(null);
        if (v == null) {
            e.setTipo(lectura.tipo() != null ? lectura.tipo() : TipoEvento.ENTRADA);
            resolver(e, MotivoAcceso.PLACA_DESCONOCIDA, lectura.forzar(), observacion);
        } else {
            Optional<EventoAcceso> ultimo = eventoRepo.findFirstByVehiculoIdOrderByOcurridoEnDescIdDesc(v.getId());
            // Anti-rebote: no se duplica una lectura reciente. Excepción: la apertura forzada del admin justo después
            // de una lectura denegada (el admin decide abrir igual) sí se registra.
            // Tampoco se reutiliza un evento de otro origen, ni uno anterior a un cambio en el usuario (su estado pudo
            // cambiar desde entonces): en esos casos las reglas se evalúan de nuevo.
            if (ultimo.isPresent()
                    && Duration.between(ultimo.get().getOcurridoEn(), ocurrido).abs().compareTo(antirrebote) < 0
                    && ultimo.get().getOrigen() == lectura.origen()
                    && !usuarioCambioDesde(v.getUsuario(), ultimo.get())
                    && !(lectura.forzar() && ultimo.get().getResultado() == ResultadoAcceso.DENEGADO)) {
                return mapeos.evento(ultimo.get(), true);
            }
            boolean dentro = eventoRepo
                    .findFirstByVehiculoIdAndResultadoOrderByOcurridoEnDescIdDesc(v.getId(), ResultadoAcceso.PERMITIDO)
                    .map(x -> x.getTipo() == TipoEvento.ENTRADA)
                    .orElse(false);
            TipoEvento tipo = lectura.tipo() != null ? lectura.tipo() : (dentro ? TipoEvento.SALIDA : TipoEvento.ENTRADA);
            Usuario u = v.getUsuario();
            e.setVehiculo(v);
            e.setUsuario(u);
            e.setTipo(tipo);
            if (tipo == TipoEvento.ENTRADA) {
                resolver(e, motivoDenegacionEntrada(u, v, dentro), lectura.forzar(), observacion);
            } else {
                // La salida siempre se permite: no se retiene un vehículo dentro.
                e.setResultado(ResultadoAcceso.PERMITIDO);
                e.setMotivo(u.getEstado() == EstadoUsuario.VENCIDO ? MotivoAcceso.SALIDA_CON_DEUDA : null);
                e.setObservacion(dentro ? observacion : unir("Sin entrada previa registrada", observacion));
            }
        }

        eventoRepo.saveAndFlush(e);
        if (e.getResultado() == ResultadoAcceso.PERMITIDO) {
            puerta.setAbiertaHasta(ahora.plus(apertura));
        }
        return mapeos.evento(e, false);
    }

    private static boolean usuarioCambioDesde(Usuario u, EventoAcceso evento) {
        return u != null && u.getActualizadoEn() != null && evento.getRegistradoEn() != null
                && u.getActualizadoEn().isAfter(evento.getRegistradoEn());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventoAccesoDTO> listar(LocalDate desde, LocalDate hasta, String placa, ResultadoAcceso resultado,
                                        Pageable pageable) {
        String placaNormalizada = placa == null || placa.isBlank() ? null : Placas.normalizar(placa);
        Specification<EventoAcceso> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("simulado")));
            if (desde != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("ocurridoEn"), Fechas.inicioDelDia(desde)));
            }
            if (hasta != null) {
                ps.add(cb.lessThan(root.get("ocurridoEn"), Fechas.inicioDelDia(hasta.plusDays(1))));
            }
            if (placaNormalizada != null) {
                ps.add(cb.equal(root.get("placaLeida"), placaNormalizada));
            }
            if (resultado != null) {
                ps.add(cb.equal(root.get("resultado"), resultado));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "ocurridoEn").and(Sort.by(Sort.Direction.DESC, "id")));
        return eventoRepo.findAll(spec, orden).map(ev -> mapeos.evento(ev, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoAccesoDTO> mios(Long usuarioId) {
        Instant desde = clock.instant().minus(30, ChronoUnit.DAYS);
        return eventoRepo.findByUsuarioIdAndSimuladoFalseAndOcurridoEnGreaterThanEqualOrderByOcurridoEnDescIdDesc(
                usuarioId, desde).stream().map(ev -> mapeos.evento(ev, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OcupacionDTO ocupacion() {
        List<Object[]> filas = eventoRepo.vehiculosDentro(Parqueadero.PRINCIPAL);
        List<Long> ids = filas.stream().map(f -> ((Number) f[0]).longValue()).toList();
        Map<Long, Vehiculo> vehiculos = vehiculoRepo.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Vehiculo::getId, Function.identity()));

        List<OcupacionDTO.VehiculoDentro> dentro = new ArrayList<>();
        Map<TipoVehiculo, Long> ocupadosPorTipo = new EnumMap<>(TipoVehiculo.class);
        for (Object[] f : filas) {
            Vehiculo v = vehiculos.get(((Number) f[0]).longValue());
            if (v == null) {
                continue;
            }
            ocupadosPorTipo.merge(v.getTipoVehiculo(), 1L, Long::sum);
            Usuario u = v.getUsuario();
            dentro.add(new OcupacionDTO.VehiculoDentro(v.getPlaca(), u.getNombre() + " " + u.getApellido(),
                    Fechas.local(instante(f[1]))));
        }
        List<OcupacionDTO.PorTipo> porTipo = Arrays.stream(TipoVehiculo.values()).map(tipo -> {
            long cupos = cupoRepo.countByParqueaderoIdAndTipoVehiculoAndActivoTrue(Parqueadero.PRINCIPAL, tipo);
            long ocupados = ocupadosPorTipo.getOrDefault(tipo, 0L);
            return new OcupacionDTO.PorTipo(tipo, cupos, ocupados, Math.max(0, cupos - ocupados));
        }).toList();
        return new OcupacionDTO(porTipo, dentro);
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Reglas de ENTRADA (RF-39), en orden. null = se permite. */
    private MotivoAcceso motivoDenegacionEntrada(Usuario u, Vehiculo v, boolean dentro) {
        if (u.isDadoDeBaja()) {
            return MotivoAcceso.USUARIO_DE_BAJA;
        }
        if (u.getEstado() == EstadoUsuario.SUSPENDIDO) {
            return MotivoAcceso.USUARIO_SUSPENDIDO;
        }
        if (u.getEstado() == EstadoUsuario.VENCIDO) {
            return MotivoAcceso.USUARIO_VENCIDO;
        }
        if (u.getCupo() == null) {
            return MotivoAcceso.SIN_CUPO;
        }
        if (u.getCupo().getTipoVehiculo() != v.getTipoVehiculo()) {
            return MotivoAcceso.TIPO_CUPO_DISTINTO;
        }
        if (dentro) {
            return MotivoAcceso.YA_DENTRO;
        }
        return null;
    }

    /** Aplica el motivo de denegación o, si es una apertura forzada del admin, la deja PERMITIDA con FORZADO_ADMIN. */
    private void resolver(EventoAcceso e, MotivoAcceso denegacion, boolean forzar, String observacion) {
        if (denegacion == null) {
            e.setResultado(ResultadoAcceso.PERMITIDO);
            e.setObservacion(observacion);
        } else if (forzar) {
            if (observacion == null) {
                throw NegocioException.invalido("La observación es obligatoria para forzar la apertura ("
                        + denegacion + ")");
            }
            e.setResultado(ResultadoAcceso.PERMITIDO);
            e.setMotivo(MotivoAcceso.FORZADO_ADMIN);
            e.setObservacion(unir(observacion, "regla: " + denegacion));
        } else {
            e.setResultado(ResultadoAcceso.DENEGADO);
            e.setMotivo(denegacion);
            e.setObservacion(observacion);
        }
    }

    private Instant validarInstante(Instant ocurridoEn, Instant ahora) {
        if (ocurridoEn == null) {
            return ahora;
        }
        if (ocurridoEn.isAfter(ahora) || ocurridoEn.isBefore(ahora.minus(desfaseMaximo))) {
            throw NegocioException.invalido("ocurridoEn debe estar en el pasado y como mucho "
                    + desfaseMaximo.toMinutes() + " minutos antes de ahora");
        }
        return ocurridoEn;
    }

    private static Instant instante(Object valor) {
        if (valor instanceof Instant i) {
            return i;
        }
        if (valor instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        if (valor instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        return null;
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String unir(String a, String b) {
        String r = a == null ? b : (b == null ? a : a + " · " + b);
        return r == null ? null : (r.length() > 200 ? r.substring(0, 200) : r);
    }
}
