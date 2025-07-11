package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.CamaraDTO;
import com.parqueadero.backend.entity.Camara;
import com.parqueadero.backend.repository.CamaraRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CamaraServiceImpl implements CamaraService {

    private final CamaraRepository repo;

    public CamaraServiceImpl(CamaraRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<CamaraDTO> listarCamaras() {
        return repo.findAll()
                .stream()
                .map(c -> CamaraDTO.builder()
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .url(c.getUrl())
                        .activa(c.getActiva())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public CamaraDTO actualizarEstado(Long id, Boolean activa) {
        Camara cam = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cámara no encontrada"));
        cam.setActiva(activa);
        Camara saved = repo.save(cam);
        return CamaraDTO.builder()
                .id(saved.getId())
                .nombre(saved.getNombre())
                .url(saved.getUrl())
                .activa(saved.getActiva())
                .build();
    }

    @Override
    public CamaraDTO crearCamara(CamaraDTO dto) {
        Camara cam = Camara.builder()
                .nombre(dto.getNombre())
                .url(dto.getUrl())
                .activa(dto.getActiva())
                .build();
        cam = repo.save(cam);
        return CamaraDTO.builder()
                .id(cam.getId())
                .nombre(cam.getNombre())
                .url(cam.getUrl())
                .activa(cam.getActiva())
                .build();
    }

}
