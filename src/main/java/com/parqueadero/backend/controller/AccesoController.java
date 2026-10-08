package com.parqueadero.backend.controller;

import com.parqueadero.backend.config.Rol;
import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.EventoAccesoDTO;
import com.parqueadero.backend.dto.LecturaPlacaDTO;
import com.parqueadero.backend.dto.OcupacionDTO;
import com.parqueadero.backend.entity.OrigenAcceso;
import com.parqueadero.backend.entity.ResultadoAcceso;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.service.AccesoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accesos")
@RequiredArgsConstructor
public class AccesoController {

    private final AccesoService service;

    /**
     * Lectura de placa (simulador de cámara con X-Api-Key, o panel con JWT ADMIN). 200 si se permite;
     * 403 con el mismo cuerpo si se deniega (el evento queda registrado igual).
     */
    @PostMapping("/lecturas")
    public ResponseEntity<EventoAccesoDTO> lectura(@AuthenticationPrincipal UsuarioAutenticado quien,
                                                   @Valid @RequestBody LecturaPlacaDTO dto) {
        if (dto.tipo() != null && quien.rol() != Rol.ADMIN) {
            throw NegocioException.invalido("Solo el administrador puede indicar el tipo de evento");
        }
        EventoAccesoDTO evento = service.registrar(new AccesoService.Lectura(dto.placa(), OrigenAcceso.CAMARA,
                dto.tipo(), dto.ocurridoEn() != null ? dto.ocurridoEn().toInstant() : null, dto.camaraId(), null, false));
        return ResponseEntity.status(evento.permitido() ? HttpStatus.OK : HttpStatus.FORBIDDEN).body(evento);
    }

    @GetMapping
    public Page<EventoAccesoDTO> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String placa,
            @RequestParam(required = false) ResultadoAcceso resultado,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(desde, hasta, placa, resultado, pageable);
    }

    @GetMapping("/mios")
    public List<EventoAccesoDTO> mios(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.mios(yo.id());
    }

    @GetMapping("/ocupacion")
    public OcupacionDTO ocupacion() {
        return service.ocupacion();
    }
}
