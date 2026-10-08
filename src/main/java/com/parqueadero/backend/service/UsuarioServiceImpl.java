package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.*;
import com.parqueadero.backend.entity.*;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final UsuarioRepository usuarioRepo;
    private final VehiculoRepository vehiculoRepo;
    private final CupoRepository cupoRepo;
    private final PagoRepository pagoRepo;
    private final ParqueaderoRepository parqueaderoRepo;
    private final PasswordEncoder encoder;
    private final PoliticaIntentos intentos;
    private final Mapeos mapeos;
    private final Clock clock;
    private final long codigoHoras;
    private final int diasGracia;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepo, VehiculoRepository vehiculoRepo, CupoRepository cupoRepo,
                              PagoRepository pagoRepo, ParqueaderoRepository parqueaderoRepo, PasswordEncoder encoder,
                              PoliticaIntentos intentos, Mapeos mapeos, Clock clock,
                              @Value("${codigo-validacion.horas:72}") long codigoHoras,
                              @Value("${usuarios.vencimiento.dias-gracia:5}") int diasGracia) {
        this.usuarioRepo = usuarioRepo;
        this.vehiculoRepo = vehiculoRepo;
        this.cupoRepo = cupoRepo;
        this.pagoRepo = pagoRepo;
        this.parqueaderoRepo = parqueaderoRepo;
        this.encoder = encoder;
        this.intentos = intentos;
        this.mapeos = mapeos;
        this.clock = clock;
        this.codigoHoras = codigoHoras;
        this.diasGracia = diasGracia;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar(EstadoUsuario estado, boolean incluirBajas) {
        List<Usuario> usuarios = estado == null
                ? usuarioRepo.listar(incluirBajas)
                : usuarioRepo.listarPorEstado(estado, incluirBajas);
        return usuarios.stream().map(mapeos::usuario).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDetalleDTO obtenerDetalle(Long id) {
        Usuario u = buscar(id);
        UsuarioDTO dto = mapeos.usuario(u);
        PagoDTO ultimoPago = pagoRepo.findFirstByUsuarioIdOrderByCreadoEnDesc(id).map(mapeos::pago).orElse(null);
        return new UsuarioDetalleDTO(dto, dto.vehiculo(), dto.cupo(), ultimoPago, dto.vigenteHasta());
    }

    @Override
    @Transactional
    public UsuarioAltaRespuestaDTO crear(UsuarioAltaDTO dto) {
        if (usuarioRepo.existsByTelefonoAndDadoDeBajaEnIsNullAndSimuladoFalse(dto.telefono())) {
            throw NegocioException.conflicto("Ya existe un usuario con ese teléfono");
        }
        String placa = validarVehiculo(dto.vehiculo());
        Cupo cupo = cupoAsignable(dto.cupoId(), dto.vehiculo().tipoVehiculo(), null);
        if (vehiculoRepo.existsByPlacaAndActivoTrueAndSimuladoFalse(placa)) {
            throw NegocioException.conflicto("Ya existe un vehículo activo con esa placa");
        }

        Usuario u = new Usuario();
        u.setParqueadero(parqueaderoRepo.getReferenceById(Parqueadero.PRINCIPAL));
        u.setCupo(cupo);
        u.setTelefono(dto.telefono());
        u.setNombre(dto.nombre().trim());
        u.setApellido(dto.apellido().trim());
        u.setEstado(EstadoUsuario.VENCIDO);
        String codigo = generarCodigo();
        Instant expira = clock.instant().plus(codigoHoras, ChronoUnit.HOURS);
        u.setCodigoValidacionHash(encoder.encode(codigo));
        u.setCodigoValidacionExpiraEn(expira);
        usuarioRepo.save(u);

        vehiculoRepo.save(nuevoVehiculo(u, placa, dto.vehiculo()));
        usuarioRepo.flush();
        return new UsuarioAltaRespuestaDTO(mapeos.usuario(u), codigo, Fechas.local(expira));
    }

    @Override
    @Transactional
    public UsuarioDTO actualizar(Long id, UsuarioActualizacionDTO dto) {
        Usuario u = buscarVigente(id);
        if (dto.telefono() != null && !dto.telefono().equals(u.getTelefono())) {
            if (usuarioRepo.existsByTelefonoAndDadoDeBajaEnIsNullAndSimuladoFalse(dto.telefono())) {
                throw NegocioException.conflicto("Ya existe un usuario con ese teléfono");
            }
            u.setTelefono(dto.telefono());
        }
        if (dto.nombre() != null) {
            u.setNombre(dto.nombre().trim());
        }
        if (dto.apellido() != null) {
            u.setApellido(dto.apellido().trim());
        }
        if (dto.cupoId() != null && (u.getCupo() == null || !dto.cupoId().equals(u.getCupo().getId()))) {
            Vehiculo v = mapeos.vehiculoActivo(u);
            if (v == null) {
                throw NegocioException.conflicto("El usuario no tiene vehículo activo");
            }
            u.setCupo(cupoAsignable(dto.cupoId(), v.getTipoVehiculo(), u.getId()));
        }
        usuarioRepo.flush();
        return mapeos.usuario(u);
    }

    @Override
    @Transactional
    public VehiculoDTO cambiarVehiculo(Long id, VehiculoDTO dto) {
        Usuario u = buscarVigente(id);
        String placa = validarVehiculo(dto);
        if (u.getCupo() != null && u.getCupo().getTipoVehiculo() != dto.tipoVehiculo()) {
            throw NegocioException.invalido("El tipo de vehículo no coincide con el del cupo asignado");
        }
        Vehiculo anterior = mapeos.vehiculoActivo(u);
        if (anterior != null) {
            anterior.setActivo(false);
            vehiculoRepo.flush();
        }
        if (vehiculoRepo.existsByPlacaAndActivoTrueAndSimuladoFalse(placa)) {
            throw NegocioException.conflicto("Ya existe un vehículo activo con esa placa");
        }
        Vehiculo nuevo = vehiculoRepo.saveAndFlush(nuevoVehiculo(u, placa, dto));
        return mapeos.vehiculo(nuevo);
    }

    @Override
    @Transactional
    public UsuarioDTO cambiarEstado(Long id, CambioEstadoDTO dto) {
        Usuario u = buscarVigente(id);
        switch (dto.accion()) {
            case SUSPENDER -> {
                if (dto.motivo() == null || dto.motivo().isBlank()) {
                    throw NegocioException.invalido("El motivo de la suspensión es obligatorio");
                }
                u.setEstado(EstadoUsuario.SUSPENDIDO);
                u.setSuspendidoMotivo(dto.motivo().trim());
            }
            case REACTIVAR -> {
                if (u.getEstado() != EstadoUsuario.SUSPENDIDO) {
                    throw NegocioException.conflicto("El usuario no está suspendido");
                }
                u.setSuspendidoMotivo(null);
                u.setEstado(estadoSegunPagos(u));
            }
        }
        usuarioRepo.flush();
        return mapeos.usuario(u);
    }

    @Override
    @Transactional
    public CodigoValidacionDTO regenerarCodigo(Long id) {
        Usuario u = buscarVigente(id);
        String codigo = generarCodigo();
        Instant expira = clock.instant().plus(codigoHoras, ChronoUnit.HOURS);
        u.setCodigoValidacionHash(encoder.encode(codigo));
        u.setCodigoValidacionExpiraEn(expira);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        return new CodigoValidacionDTO(codigo, Fechas.local(expira));
    }

    @Override
    @Transactional
    public void darDeBaja(Long id) {
        Usuario u = buscarVigente(id);
        u.setDadoDeBajaEn(clock.instant());
        u.setCupo(null);
        u.setCodigoValidacionHash(null);
        u.setCodigoValidacionExpiraEn(null);
        Vehiculo v = mapeos.vehiculoActivo(u);
        if (v != null) {
            v.setActivo(false);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioDTO yo(Long id) {
        return mapeos.usuario(buscarVigente(id));
    }

    @Override
    @Transactional(noRollbackFor = NegocioException.class)
    public void cambiarPin(Long id, CambioPinDTO dto) {
        Usuario u = exigirOperable(id, true);
        if (ReglasPin.esTrivial(dto.pinNuevo())) {
            throw NegocioException.invalido("El PIN es demasiado fácil de adivinar");
        }
        if (u.getPinHash() == null || !encoder.matches(dto.pinActual(), u.getPinHash())) {
            PoliticaIntentos.Estado e = intentos.registrarFallo(u.getIntentosFallidos());
            u.setIntentosFallidos(e.intentosFallidos());
            u.setBloqueadoHasta(e.bloqueadoHasta());
            throw NegocioException.noAutenticado("PIN actual incorrecto");
        }
        u.setPinHash(encoder.encode(dto.pinNuevo()));
        u.setIntentosFallidos(0);
    }

    @Override
    @Transactional
    public int actualizarVencimientos(LocalDate hoy) {
        return usuarioRepo.marcarVencidos(hoy, diasGracia);
    }

    @Override
    @Transactional
    public void recalcularEstado(Usuario usuario) {
        if (usuario.getEstado() != EstadoUsuario.SUSPENDIDO) {
            usuario.setEstado(estadoSegunPagos(usuario));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario exigirOperable(Long id, boolean rechazarSuspendido) {
        Usuario u = usuarioRepo.findById(id).orElseThrow(() -> NegocioException.prohibido("Cuenta no disponible"));
        if (u.isDadoDeBaja() || u.isSimulado()) {
            throw NegocioException.prohibido("Cuenta no disponible");
        }
        if (intentos.estaBloqueado(u.getBloqueadoHasta())) {
            throw NegocioException.prohibido("Cuenta bloqueada temporalmente");
        }
        if (rechazarSuspendido && u.getEstado() == EstadoUsuario.SUSPENDIDO) {
            throw NegocioException.prohibido("Cuenta suspendida: " + Objects.toString(u.getSuspendidoMotivo(), ""));
        }
        return u;
    }

    // ---------------------------------------------------------------------------------------------------------

    private EstadoUsuario estadoSegunPagos(Usuario u) {
        LocalDate hoy = LocalDate.now(clock);
        return pagoRepo.tieneVigencia(u.getId(), hoy.minusDays(diasGracia)) ? EstadoUsuario.ACTIVO : EstadoUsuario.VENCIDO;
    }

    private Usuario buscar(Long id) {
        return usuarioRepo.findById(id)
                .filter(u -> !u.isSimulado())
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
    }

    private Usuario buscarVigente(Long id) {
        Usuario u = buscar(id);
        if (u.isDadoDeBaja()) {
            throw NegocioException.conflicto("El usuario está dado de baja");
        }
        return u;
    }

    /** Cupo existente, activo, del tipo del vehículo y libre (o ya asignado al propio usuario). */
    private Cupo cupoAsignable(Long cupoId, TipoVehiculo tipo, Long usuarioId) {
        Cupo cupo = cupoRepo.findById(cupoId).orElseThrow(() -> NegocioException.invalido("El cupo no existe"));
        if (!cupo.isActivo()) {
            throw NegocioException.conflicto("El cupo está inactivo");
        }
        if (cupo.getTipoVehiculo() != tipo) {
            throw NegocioException.invalido("El cupo es de tipo " + cupo.getTipoVehiculo()
                    + " y el vehículo de tipo " + tipo);
        }
        usuarioRepo.findByCupoIdAndDadoDeBajaEnIsNullAndSimuladoFalse(cupoId)
                .filter(otro -> !otro.getId().equals(usuarioId))
                .ifPresent(otro -> {
                    throw NegocioException.conflicto("El cupo ya está asignado a otro usuario");
                });
        return cupo;
    }

    /** Normaliza la placa y valida formato y carrocería según el tipo. Devuelve la placa normalizada. */
    private String validarVehiculo(VehiculoDTO dto) {
        String placa = Placas.normalizar(dto.placa());
        if (!Placas.formatoValido(placa, dto.tipoVehiculo())) {
            throw NegocioException.invalido(dto.tipoVehiculo() == TipoVehiculo.CARRO
                    ? "Placa de carro inválida (formato AAA123)"
                    : "Placa de moto inválida (formato AAA12 o AAA12A)");
        }
        if (dto.tipoVehiculo() == TipoVehiculo.CARRO && dto.carroceria() == null) {
            throw NegocioException.invalido("La carrocería es obligatoria para carros");
        }
        if (dto.tipoVehiculo() == TipoVehiculo.MOTO && dto.carroceria() != null) {
            throw NegocioException.invalido("Las motos no llevan carrocería");
        }
        return placa;
    }

    private Vehiculo nuevoVehiculo(Usuario u, String placa, VehiculoDTO dto) {
        Vehiculo v = new Vehiculo();
        v.setUsuario(u);
        v.setPlaca(placa);
        v.setTipoVehiculo(dto.tipoVehiculo());
        v.setCarroceria(dto.carroceria());
        v.setColor(dto.color().trim());
        v.setMarca(dto.marca() == null || dto.marca().isBlank() ? null : dto.marca().trim());
        v.setActivo(true);
        return v;
    }

    private static String generarCodigo() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
