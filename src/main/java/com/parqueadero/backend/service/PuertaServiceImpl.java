package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.PuertaDTO;
import com.parqueadero.backend.entity.Puerta;
import com.parqueadero.backend.repository.PuertaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PuertaServiceImpl implements PuertaService {

    private final PuertaRepository repo;

    @Override
    public PuertaDTO obtenerEstado() {
        Puerta puerta = repo.findById(1L).orElseGet(() -> {
            
            Puerta nueva = Puerta.builder().id(1L).abierta(false).build();
            return repo.save(nueva);
        });

        return PuertaDTO.builder().abierta(puerta.getAbierta()).build();
    }

    @Override
    public PuertaDTO actualizarEstado(Boolean nuevoEstado) {
        Puerta puerta = repo.findById(1L).orElseThrow(() -> new EntityNotFoundException("Estado de puerta no encontrado"));
        puerta.setAbierta(nuevoEstado);
        repo.save(puerta);
        return PuertaDTO.builder().abierta(puerta.getAbierta()).build();
    }
}
