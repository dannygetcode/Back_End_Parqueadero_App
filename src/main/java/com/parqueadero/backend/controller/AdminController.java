package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.AdminLoginRequest;
import com.parqueadero.backend.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JwtService jwtService;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    public AdminController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body("Faltan campos obligatorios");
        }

        if (request.getUsername().equals(adminUsername) && request.getPassword().equals(adminPassword)) {
            String token = jwtService.generarToken(request.getUsername());
            return ResponseEntity.ok().body(token);
        }

        return ResponseEntity.status(401).body("Credenciales inválidas");
    }
}
