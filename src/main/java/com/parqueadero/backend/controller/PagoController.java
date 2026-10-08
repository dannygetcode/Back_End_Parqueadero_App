package com.parqueadero.backend.controller;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.AprobacionPagoDTO;
import com.parqueadero.backend.dto.PagoDTO;
import com.parqueadero.backend.dto.PeriodoDTO;
import com.parqueadero.backend.dto.RechazoPagoDTO;
import com.parqueadero.backend.entity.EstadoPago;
import com.parqueadero.backend.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService service;

    @GetMapping("/proximo-periodo")
    public PeriodoDTO proximoPeriodo(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.proximoPeriodo(yo.id());
    }

    /** Sube el comprobante (JPEG/PNG, máx. 5 MB). El usuario sale del token y el periodo lo calcula el backend. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PagoDTO crear(@AuthenticationPrincipal UsuarioAutenticado yo,
                         @RequestPart(name = "comprobante", required = false) MultipartFile comprobante) {
        return service.crear(yo.id(), comprobante);
    }

    /** Cortesía registrada por el admin (Fase 1: solo montoConfirmado = 0, observación obligatoria). */
    @PostMapping(path = "/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PagoDTO crearManual(@RequestParam Long usuarioId,
                               @RequestParam Integer montoConfirmado,
                               @RequestParam(required = false) String observacion,
                               @RequestPart(name = "comprobante", required = false) MultipartFile comprobante) {
        return service.crearManual(usuarioId, montoConfirmado, observacion, comprobante);
    }

    @GetMapping
    public Page<PagoDTO> listar(@RequestParam(required = false) EstadoPago estado,
                                @RequestParam(required = false) Long usuarioId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(estado, usuarioId, desde, hasta, pageable);
    }

    @GetMapping("/mios")
    public List<PagoDTO> mios(@AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.mios(yo.id());
    }

    @GetMapping("/{id}")
    public PagoDTO obtener(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
        return service.obtener(id, yo);
    }

    @GetMapping("/{id}/comprobante")
    public ResponseEntity<Resource> comprobante(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado yo) {
        PagoService.Comprobante c = service.comprobante(id, yo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.tipo()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(c.archivo()));
    }

    @PutMapping("/{id}/aprobar")
    public PagoDTO aprobar(@PathVariable Long id, @Valid @RequestBody AprobacionPagoDTO dto) {
        return service.aprobar(id, dto);
    }

    @PutMapping("/{id}/rechazar")
    public PagoDTO rechazar(@PathVariable Long id, @Valid @RequestBody RechazoPagoDTO dto) {
        return service.rechazar(id, dto);
    }
}
