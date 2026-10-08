package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.TarifaDTO;
import com.parqueadero.backend.service.TarifaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tarifas")
@RequiredArgsConstructor
public class TarifaController {

    private final TarifaService service;

    @GetMapping
    public List<TarifaDTO> listar(@RequestParam(defaultValue = "false") boolean vigentes) {
        return service.listar(vigentes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaDTO crear(@Valid @RequestBody TarifaDTO dto) {
        return service.crear(dto);
    }
}
