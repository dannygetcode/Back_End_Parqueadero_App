package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CupoActualizacionDTO;
import com.parqueadero.backend.dto.CupoAltaDTO;
import com.parqueadero.backend.dto.CupoDTO;
import com.parqueadero.backend.entity.Cupo;
import com.parqueadero.backend.entity.EstadoUsuario;
import com.parqueadero.backend.entity.Parqueadero;
import com.parqueadero.backend.entity.Usuario;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.CupoRepository;
import com.parqueadero.backend.repository.ParqueaderoRepository;
import com.parqueadero.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class CupoServiceImpl implements CupoService {

    private final CupoRepository cupoRepo;
    private final UsuarioRepository usuarioRepo;
    private final ParqueaderoRepository parqueaderoRepo;
    private final Mapeos mapeos;
    private final Clock clock;
    private final int diasGracia;

    public CupoServiceImpl(CupoRepository cupoRepo, UsuarioRepository usuarioRepo,
                           ParqueaderoRepository parqueaderoRepo, Mapeos mapeos, Clock clock,
                           @Value("${usuarios.vencimiento.dias-gracia:5}") int diasGracia) {
        this.cupoRepo = cupoRepo;
        this.usuarioRepo = usuarioRepo;
        this.parqueaderoRepo = parqueaderoRepo;
        this.mapeos = mapeos;
        this.clock = clock;
        this.diasGracia = diasGracia;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CupoDTO> listar() {
        return cupoRepo.findByParqueaderoIdOrderByCodigoAsc(Parqueadero.PRINCIPAL).stream().map(this::dto).toList();
    }

    @Override
    @Transactional
    public CupoDTO crear(CupoAltaDTO dto) {
        if (cupoRepo.existsByParqueaderoIdAndCodigo(Parqueadero.PRINCIPAL, dto.codigo())) {
            throw NegocioException.conflicto("Ya existe un cupo con ese código");
        }
        Cupo c = new Cupo();
        c.setParqueadero(parqueaderoRepo.getReferenceById(Parqueadero.PRINCIPAL));
        c.setCodigo(dto.codigo());
        c.setTipoVehiculo(dto.tipoVehiculo());
        c.setActivo(true);
        return dto(cupoRepo.saveAndFlush(c));
    }

    @Override
    @Transactional
    public CupoDTO actualizar(Long id, CupoActualizacionDTO dto) {
        Cupo c = cupoRepo.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Cupo no encontrado"));
        if (!dto.activo() && usuarioRepo.findByCupoIdAndDadoDeBajaEnIsNullAndSimuladoFalse(id).isPresent()) {
            throw NegocioException.conflicto("No se puede desactivar un cupo asignado a un usuario");
        }
        c.setActivo(dto.activo());
        return dto(c);
    }

    /**
     * ocupado = asignado a un usuario no dado de baja, en cualquier estado (el cupo no se puede reasignar sin darlo
     * de baja o cambiarle el cupo). vigente = ese usuario está ACTIVO.
     */
    private CupoDTO dto(Cupo c) {
        Usuario u = usuarioRepo.findByCupoIdAndDadoDeBajaEnIsNullAndSimuladoFalse(c.getId()).orElse(null);
        return new CupoDTO(c.getId(), c.getCodigo(), c.getTipoVehiculo(), c.isActivo(),
                u != null ? u.getId() : null, u != null, u != null && u.getEstado() == EstadoUsuario.ACTIVO);
    }
}
