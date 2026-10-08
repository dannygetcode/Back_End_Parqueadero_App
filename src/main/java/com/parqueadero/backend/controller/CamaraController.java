package com.parqueadero.backend.controller;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.CamaraDTO;
import com.parqueadero.backend.dto.CamaraEstadoDTO;
import com.parqueadero.backend.service.CamaraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lectura: ADMIN y USUARIO. Escritura: solo ADMIN. CORS solo en SecurityConfig (sin @CrossOrigin). */
@RestController
@RequestMapping("/api/camaras")
@RequiredArgsConstructor
public class CamaraController {

    private final CamaraService servicio;

    /** ADMIN: {@code List<CamaraDTO>} (con url). USUARIO: {@code List<CamaraUsuarioDTO>} (sin url). */
    @GetMapping
    public List<?> listar(@AuthenticationPrincipal UsuarioAutenticado quien) {
        return quien != null && quien.esAdmin() ? servicio.listarCamaras() : servicio.listarCamarasParaUsuario();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CamaraDTO crearCamara(@Valid @RequestBody CamaraDTO dto) {
        return servicio.crearCamara(dto);
    }

    @PutMapping("/{id}")
    public CamaraDTO actualizarEstado(@PathVariable Long id, @Valid @RequestBody CamaraEstadoDTO dto) {
        return servicio.actualizarEstado(id, dto.activa());
    }
}
