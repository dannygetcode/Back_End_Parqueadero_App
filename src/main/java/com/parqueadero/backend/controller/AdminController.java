package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.AdminLoginRequest;
import com.parqueadero.backend.dto.TokenDTO;
import com.parqueadero.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;

    /** 200 TokenDTO; 401 credenciales inválidas; 423 bloqueado. */
    @PostMapping("/login")
    public TokenDTO login(@Valid @RequestBody AdminLoginRequest request) {
        return authService.loginAdmin(request);
    }
}
