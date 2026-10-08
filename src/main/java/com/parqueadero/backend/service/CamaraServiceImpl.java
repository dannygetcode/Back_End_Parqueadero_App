package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CamaraDTO;
import com.parqueadero.backend.dto.CamaraUsuarioDTO;
import com.parqueadero.backend.entity.Camara;
import com.parqueadero.backend.entity.Parqueadero;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.CamaraRepository;
import com.parqueadero.backend.repository.ParqueaderoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CamaraServiceImpl implements CamaraService {

    private final CamaraRepository repo;
    private final ParqueaderoRepository parqueaderoRepo;
    private final Mapeos mapeos;

    @Override
    @Transactional(readOnly = true)
    public List<CamaraDTO> listarCamaras() {
        return repo.findAllByOrderByIdAsc().stream().map(mapeos::camara).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CamaraUsuarioDTO> listarCamarasParaUsuario() {
        return repo.findAllByOrderByIdAsc().stream()
                .map(c -> new CamaraUsuarioDTO(c.getId(), c.getNombre(), c.isActiva()))
                .toList();
    }

    @Override
    @Transactional
    public CamaraDTO actualizarEstado(Long id, boolean activa) {
        Camara cam = repo.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Cámara no encontrada"));
        cam.setActiva(activa);
        return mapeos.camara(cam);
    }

    @Override
    @Transactional
    public CamaraDTO crearCamara(CamaraDTO dto) {
        Camara cam = new Camara();
        cam.setParqueadero(parqueaderoRepo.getReferenceById(Parqueadero.PRINCIPAL));
        cam.setNombre(dto.nombre().trim());
        cam.setUrl(dto.url() == null || dto.url().isBlank() ? null : dto.url().trim());
        cam.setActiva(dto.activa() == null || dto.activa());
        cam.setSimulada(dto.simulada() == null || dto.simulada());
        return mapeos.camara(repo.saveAndFlush(cam));
    }
}
