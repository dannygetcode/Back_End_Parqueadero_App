package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.CamaraDTO;
import com.parqueadero.backend.service.CamaraService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(
  origins = "*",
  methods = {
    RequestMethod.GET,
    RequestMethod.POST,    
    RequestMethod.PUT,
    RequestMethod.DELETE,  
    RequestMethod.OPTIONS
  }
)

@RestController
@RequestMapping("/api/camaras")
public class CamaraController {

    private final CamaraService servicio;

    public CamaraController(CamaraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<CamaraDTO> listar() {
        return servicio.listarCamaras();
    }

    @PostMapping
    public ResponseEntity<CamaraDTO> crearCamara(@RequestBody CamaraDTO dto) {
        CamaraDTO creada = servicio.crearCamara(dto);
        return ResponseEntity.ok(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CamaraDTO> actualizarEstado(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> payload) {
        Boolean activa = payload.get("activa");
        CamaraDTO updated = servicio.actualizarEstado(id, activa);
        return ResponseEntity.ok(updated);
    }

}
