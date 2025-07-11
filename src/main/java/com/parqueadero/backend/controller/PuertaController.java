package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.PuertaDTO;
import com.parqueadero.backend.service.PuertaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/puerta")
@RequiredArgsConstructor
public class PuertaController {

    private final PuertaService service;

    @GetMapping
    public ResponseEntity<PuertaDTO> estadoActual() {
        return ResponseEntity.ok(service.obtenerEstado());
    }

    @PutMapping
    public ResponseEntity<PuertaDTO> actualizarEstado(@RequestBody PuertaDTO dto) {
        return ResponseEntity.ok(service.actualizarEstado(dto.getAbierta()));
    }
}
