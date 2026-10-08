package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.AvisoPrivacidadDTO;
import com.parqueadero.backend.service.LegalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoints públicos sin datos: healthcheck y aviso de privacidad. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicoController {

    private final LegalService legalService;

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/legal/aviso-privacidad")
    public AvisoPrivacidadDTO avisoPrivacidad() {
        return legalService.avisoPrivacidad();
    }
}
