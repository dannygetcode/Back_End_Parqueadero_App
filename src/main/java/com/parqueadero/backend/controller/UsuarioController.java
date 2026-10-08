package com.parqueadero.backend.controller;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.*;
import com.parqueadero.backend.entity.EstadoUsuario;
import com.parqueadero.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /yo y /yo/pin son del USUARIO autenticado; el resto, del ADMIN (reglas en SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;

    @GetMapping("/yo")
    public UsuarioDTO yo(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.yo(yo.id());
    }

    @PutMapping("/yo/pin")
    public ResponseEntity<Void> cambiarPin(@AuthenticationPrincipal UsuarioAutenticado yo,
                                           @Valid @RequestBody CambioPinDTO dto) {
        service.cambiarPin(yo.id(), dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<UsuarioDTO> listar(@RequestParam(required = false) EstadoUsuario estado,
                                   @RequestParam(defaultValue = "false") boolean incluirBajas) {
        return service.listar(estado, incluirBajas);
    }

    @GetMapping("/{id}")
    public UsuarioDetalleDTO obtener(@PathVariable Long id) {
        return service.obtenerDetalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioAltaRespuestaDTO crear(@Valid @RequestBody UsuarioAltaDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public UsuarioDTO actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioActualizacionDTO dto) {
        return service.actualizar(id, dto);
    }

    @PutMapping("/{id}/vehiculo")
    public VehiculoDTO cambiarVehiculo(@PathVariable Long id, @Valid @RequestBody VehiculoDTO dto) {
        return service.cambiarVehiculo(id, dto);
    }

    @PutMapping("/{id}/estado")
    public UsuarioDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoDTO dto) {
        return service.cambiarEstado(id, dto);
    }

    @PostMapping("/{id}/codigo")
    public CodigoValidacionDTO regenerarCodigo(@PathVariable Long id) {
        return service.regenerarCodigo(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Long id) {
        service.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
