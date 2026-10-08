package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.ActivacionDTO;
import com.parqueadero.backend.dto.LoginUsuarioDTO;
import com.parqueadero.backend.dto.TokenDTO;
import com.parqueadero.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Autenticación del usuario mensual (públicos). */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 204; 400 PIN o consentimiento inválidos; 401 teléfono o código incorrectos; 423 bloqueado. */
    @PostMapping("/activar")
    public ResponseEntity<Void> activar(@Valid @RequestBody ActivacionDTO dto) {
        authService.activar(dto);
        return ResponseEntity.noContent().build();
    }

    /** 200 TokenDTO; 401 teléfono o PIN incorrectos; 423 bloqueado. */
    @PostMapping("/login")
    public TokenDTO login(@Valid @RequestBody LoginUsuarioDTO dto) {
        return authService.loginUsuario(dto);
    }
}
