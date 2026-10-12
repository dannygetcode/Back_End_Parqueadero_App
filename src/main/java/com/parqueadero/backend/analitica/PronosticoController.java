package com.parqueadero.backend.analitica;

import com.parqueadero.backend.exception.NegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxy del pronóstico (rol ADMIN por SecurityConfig). Solo reenvía los parámetros de la lista blanca
 * (incluirSimulados, horizonteDias); cualquier otro se ignora.
 */
@RestController
@RequestMapping("/api/analitica/pronostico")
@RequiredArgsConstructor
public class PronosticoController {

    private static final int HORIZONTE_MAX = 14;

    private final PronosticoService service;

    @GetMapping("/vacancia")
    public ResponseEntity<String> vacancia(@RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return json(service.vacancia(incluirSimulados));
    }

    @GetMapping("/ocupacion")
    public ResponseEntity<String> ocupacion(@RequestParam(defaultValue = "7") int horizonteDias,
                                            @RequestParam(defaultValue = "false") boolean incluirSimulados) {
        if (horizonteDias < 1 || horizonteDias > HORIZONTE_MAX) {
            throw NegocioException.invalido("horizonteDias debe estar entre 1 y " + HORIZONTE_MAX);
        }
        return json(service.ocupacion(horizonteDias, incluirSimulados));
    }

    @GetMapping("/llegadas")
    public ResponseEntity<String> llegadas(@RequestParam(defaultValue = "false") boolean incluirSimulados) {
        return json(service.llegadas(incluirSimulados));
    }

    private static ResponseEntity<String> json(String cuerpo) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }
}
