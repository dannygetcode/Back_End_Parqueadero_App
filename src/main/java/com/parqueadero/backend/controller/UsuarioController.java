package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.UsuarioDTO;
import com.parqueadero.backend.dto.UsuarioRegistroDTO;
import com.parqueadero.backend.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.parqueadero.backend.service.JwtService;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService service;
    private final JwtService jwtService;

    @PostMapping("/solicitar-validacion")
    public ResponseEntity<UsuarioDTO> solicitar(@RequestParam String telefono) {
        UsuarioDTO dto = service.solicitarValidacion(telefono);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/validar")
    public ResponseEntity<Void> validar(
            @RequestParam String telefono,
            @RequestParam String codigo,
            @RequestParam String nuevoPin) {
        service.validarUsuario(telefono, codigo, nuevoPin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestParam String telefono,
            @RequestParam String pin) {
        String subject = service.autenticar(telefono, pin);
        String token = jwtService.generarToken(subject);
        return ResponseEntity.ok(Collections.singletonMap("token", token));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> listarTodos() {
        return ResponseEntity.ok(service.listarTodos());
    }

    @PostMapping("/registro")
    public ResponseEntity<UsuarioDTO> registrar(@RequestBody UsuarioRegistroDTO dto) {
        UsuarioDTO creado = service.registrarUsuario(dto);
        return ResponseEntity.ok(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody UsuarioDTO dto) {
        service.actualizarUsuario(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificar-codigo")
    public ResponseEntity<Void> verificarCodigo(@RequestParam String telefono, @RequestParam String codigo) {
        service.verificarCodigo(telefono, codigo);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        service.cambiarEstado(id, estado);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/yo")
    public ResponseEntity<UsuarioDTO> obtenerMiUsuario(@RequestHeader("Authorization") String token) {
        String telefono = jwtService.extraerSubject(token.replace("Bearer ", ""));
        UsuarioDTO dto = service.buscarPorTelefono(telefono);
        return ResponseEntity.ok(dto);
    }

}
