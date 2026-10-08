package com.parqueadero.backend.service;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.AprobacionPagoDTO;
import com.parqueadero.backend.dto.PagoDTO;
import com.parqueadero.backend.dto.PeriodoDTO;
import com.parqueadero.backend.dto.RechazoPagoDTO;
import com.parqueadero.backend.entity.EstadoPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Sustituye a PaymentService (ADR 0002, 0005). */
public interface PagoService {

    record Comprobante(Path archivo, String tipo) {
    }

    PeriodoDTO proximoPeriodo(Long usuarioId);

    PagoDTO crear(Long usuarioId, MultipartFile comprobante);

    /** Cortesía registrada por el admin (en Fase 1 solo montoConfirmado = 0). Nace APROBADO. */
    PagoDTO crearManual(Long usuarioId, Integer montoConfirmado, String observacion, MultipartFile comprobante);

    Page<PagoDTO> listar(EstadoPago estado, Long usuarioId, LocalDate desde, LocalDate hasta, Pageable pageable);

    List<PagoDTO> mios(Long usuarioId);

    /** ADMIN cualquiera; USUARIO solo los suyos (si no, 404). */
    PagoDTO obtener(Long id, UsuarioAutenticado quien);

    Comprobante comprobante(Long id, UsuarioAutenticado quien);

    PagoDTO aprobar(Long id, AprobacionPagoDTO dto);

    PagoDTO rechazar(Long id, RechazoPagoDTO dto);
}
