package com.parqueadero.backend.controller;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.PuertaComandoDTO;
import com.parqueadero.backend.dto.PuertaDTO;
import com.parqueadero.backend.service.PuertaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/puerta")
@RequiredArgsConstructor
public class PuertaController {

    private final PuertaService service;

    @GetMapping
    public PuertaDTO estadoActual(@AuthenticationPrincipal UsuarioAutenticado quien) {
        return service.obtenerEstado(quien);
    }

    /** 200 con el evento; 403 con el mismo cuerpo si el acceso se deniega (queda registrado). */
    @PutMapping
    public ResponseEntity<PuertaDTO> comando(@AuthenticationPrincipal UsuarioAutenticado quien,
                                             @Valid @RequestBody PuertaComandoDTO dto) {
        PuertaDTO r = service.comando(dto, quien);
        boolean denegado = r.evento() != null && !r.evento().permitido();
        return ResponseEntity.status(denegado ? HttpStatus.FORBIDDEN : HttpStatus.OK).body(r);
    }
}
