package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.CupoActualizacionDTO;
import com.parqueadero.backend.dto.CupoAltaDTO;
import com.parqueadero.backend.dto.CupoDTO;
import com.parqueadero.backend.service.CupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cupos")
@RequiredArgsConstructor
public class CupoController {

    private final CupoService service;

    @GetMapping
    public List<CupoDTO> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CupoDTO crear(@Valid @RequestBody CupoAltaDTO dto) {
        return service.crear(dto);
    }

    @PutMapping("/{id}")
    public CupoDTO actualizar(@PathVariable Long id, @Valid @RequestBody CupoActualizacionDTO dto) {
        return service.actualizar(id, dto);
    }
}
