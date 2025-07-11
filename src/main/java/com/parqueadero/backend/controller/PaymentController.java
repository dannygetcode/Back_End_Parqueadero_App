package com.parqueadero.backend.controller;

import com.parqueadero.backend.dto.PaymentDTO;
import com.parqueadero.backend.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PaymentController {

    private final PaymentService svc;

    public PaymentController(PaymentService svc) {
        this.svc = svc;
    }

    /**
     * Crea un nuevo pago. Llama al OCR, guarda la imagen y los datos.
     */
    @PostMapping
    public ResponseEntity<PaymentDTO> create(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam (required = false) MultipartFile image, @RequestParam String placa) throws Exception {
        PaymentDTO created = svc.create(userId, start, end, placa, image);
        return ResponseEntity.ok(created);
    }

    /**
     * Lista todos los pagos.
     */
    @GetMapping
    public ResponseEntity<List<PaymentDTO>> list() {
        List<PaymentDTO> all = svc.listAll();
        return ResponseEntity.ok(all);
    }

    /**
     * Obtiene un pago por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentDTO> getById(@PathVariable Long id) {
        PaymentDTO dto = svc.getById(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Actualiza un pago existente.
     * Aquí recibimos un DTO con los campos a editar (fechas de servicio, etc.).
     */
    @PutMapping("/{id}")
    public ResponseEntity<PaymentDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody PaymentDTO dto) throws Exception {
        PaymentDTO updated = svc.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * Elimina un pago por su ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

}
