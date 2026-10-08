package com.parqueadero.backend.service;

import com.parqueadero.backend.config.UsuarioAutenticado;
import com.parqueadero.backend.dto.AprobacionPagoDTO;
import com.parqueadero.backend.dto.PagoDTO;
import com.parqueadero.backend.dto.PeriodoDTO;
import com.parqueadero.backend.dto.RechazoPagoDTO;
import com.parqueadero.backend.entity.*;
import com.parqueadero.backend.exception.NegocioException;
import com.parqueadero.backend.repository.PagoRepository;
import com.parqueadero.backend.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class PagoServiceImpl implements PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoServiceImpl.class);

    private final PagoRepository pagoRepo;
    private final UsuarioRepository usuarioRepo;
    private final UsuarioService usuarioService;
    private final TarifaService tarifaService;
    private final OcrService ocrService;
    private final AlmacenComprobantes almacen;
    private final Mapeos mapeos;
    private final Clock clock;
    private final int diasGracia;
    private final long maxBytes;

    public PagoServiceImpl(PagoRepository pagoRepo, UsuarioRepository usuarioRepo, UsuarioService usuarioService,
                           TarifaService tarifaService, OcrService ocrService, AlmacenComprobantes almacen,
                           Mapeos mapeos, Clock clock,
                           @Value("${usuarios.vencimiento.dias-gracia:5}") int diasGracia,
                           @Value("${spring.servlet.multipart.max-file-size:5MB}") DataSize maxTamano) {
        this.pagoRepo = pagoRepo;
        this.usuarioRepo = usuarioRepo;
        this.usuarioService = usuarioService;
        this.tarifaService = tarifaService;
        this.ocrService = ocrService;
        this.almacen = almacen;
        this.mapeos = mapeos;
        this.clock = clock;
        this.diasGracia = diasGracia;
        this.maxBytes = maxTamano.toBytes();
    }

    @Override
    @Transactional(readOnly = true)
    public PeriodoDTO proximoPeriodo(Long usuarioId) {
        Usuario u = usuarioRepo.findById(usuarioId).orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        Calculo c = calcular(u, LocalDate.now(clock));
        return new PeriodoDTO(c.periodo().inicio(), c.periodo().fin(), c.tarifa().getValorMensual());
    }

    @Override
    @Transactional
    public PagoDTO crear(Long usuarioId, MultipartFile comprobante) {
        Usuario u = usuarioService.exigirOperable(usuarioId, true);
        Archivo archivo = validarArchivo(comprobante, true);
        if (pagoRepo.existsByUsuarioIdAndEstadoAndSimuladoFalse(usuarioId, EstadoPago.PENDIENTE)) {
            throw NegocioException.conflicto("Ya tiene un pago pendiente de revisión");
        }
        if (pagoRepo.existsByUsuarioIdAndComprobanteSha256AndEstadoNot(usuarioId, archivo.sha256(), EstadoPago.RECHAZADO)) {
            throw NegocioException.conflicto("Este comprobante ya fue enviado");
        }
        LocalDate hoy = LocalDate.now(clock);
        Calculo calculo = calcular(u, hoy);

        InterpreteOcr.Resultado ocr;
        try {
            ocr = InterpreteOcr.interpretar(ocrService.parse("comprobante." + archivo.extension(), archivo.bytes()));
        } catch (RuntimeException e) {
            log.warn("OCR no disponible o con error ({}); el pago se crea con OCR FALLIDO", e.getClass().getSimpleName());
            ocr = InterpreteOcr.fallido();
        }

        Pago p = new Pago();
        p.setUsuario(u);
        p.setTarifa(calculo.tarifa());
        p.setPeriodoInicio(calculo.periodo().inicio());
        p.setPeriodoFin(calculo.periodo().fin());
        p.setMontoEsperado(calculo.tarifa().getValorMensual());
        p.setMontoOcr(ocr.monto());
        p.setFechaPagoOcr(ocr.fecha());
        p.setOcrEstado(ocr.estado());
        p.setOcrDatos(ocr.datos());
        p.setEstado(EstadoPago.PENDIENTE);
        p.setRegistradoPor(RegistradoPor.USUARIO);
        p.setComprobanteSha256(archivo.sha256());
        p.setComprobanteTipo(archivo.tipo());
        p.setPosibleDuplicado(pagoRepo.existsByComprobanteSha256AndUsuarioIdNotAndEstadoNot(
                archivo.sha256(), usuarioId, EstadoPago.RECHAZADO));
        return guardarConArchivo(p, archivo, hoy);
    }

    @Override
    @Transactional
    public PagoDTO crearManual(Long usuarioId, Integer montoConfirmado, String observacion, MultipartFile comprobante) {
        if (montoConfirmado == null || montoConfirmado != 0) {
            throw NegocioException.invalido("En esta fase el pago manual solo admite cortesías (montoConfirmado = 0)");
        }
        if (observacion == null || observacion.isBlank() || observacion.length() > 200) {
            throw NegocioException.invalido("La observación es obligatoria (máximo 200 caracteres)");
        }
        Usuario u = usuarioRepo.findById(usuarioId)
                .filter(x -> !x.isSimulado())
                .orElseThrow(() -> NegocioException.noEncontrado("Usuario no encontrado"));
        if (u.isDadoDeBaja()) {
            throw NegocioException.conflicto("El usuario está dado de baja");
        }
        Archivo archivo = validarArchivo(comprobante, false);
        LocalDate hoy = LocalDate.now(clock);
        Calculo calculo = calcular(u, hoy);

        Pago p = new Pago();
        p.setUsuario(u);
        p.setTarifa(calculo.tarifa());
        p.setPeriodoInicio(calculo.periodo().inicio());
        p.setPeriodoFin(calculo.periodo().fin());
        p.setMontoEsperado(calculo.tarifa().getValorMensual());
        p.setOcrEstado(OcrEstado.NO_APLICA);
        p.setMontoConfirmado(montoConfirmado);
        p.setObservacion(observacion.trim());
        p.setEstado(EstadoPago.APROBADO);
        p.setRegistradoPor(RegistradoPor.ADMIN);
        p.setRevisadoEn(clock.instant());
        if (archivo != null) {
            p.setComprobanteSha256(archivo.sha256());
            p.setComprobanteTipo(archivo.tipo());
        }
        PagoDTO dto = guardarConArchivo(p, archivo, hoy);
        usuarioService.recalcularEstado(u);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PagoDTO> listar(EstadoPago estado, Long usuarioId, LocalDate desde, LocalDate hasta, Pageable pageable) {
        Specification<Pago> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("simulado")));
            if (estado != null) {
                ps.add(cb.equal(root.get("estado"), estado));
            }
            if (usuarioId != null) {
                ps.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            }
            if (desde != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), Fechas.inicioDelDia(desde)));
            }
            if (hasta != null) {
                ps.add(cb.lessThan(root.get("creadoEn"), Fechas.inicioDelDia(hasta.plusDays(1))));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Pageable orden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id")));
        return pagoRepo.findAll(spec, orden).map(mapeos::pago);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoDTO> mios(Long usuarioId) {
        return pagoRepo.findByUsuarioIdAndSimuladoFalseOrderByCreadoEnDesc(usuarioId).stream().map(mapeos::pago).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagoDTO obtener(Long id, UsuarioAutenticado quien) {
        return mapeos.pago(buscarVisible(id, quien));
    }

    @Override
    @Transactional(readOnly = true)
    public Comprobante comprobante(Long id, UsuarioAutenticado quien) {
        Pago p = buscarVisible(id, quien);
        Path archivo = almacen.abrir(p.getComprobanteRuta());
        if (archivo == null) {
            throw NegocioException.noEncontrado("El pago no tiene comprobante");
        }
        return new Comprobante(archivo, p.getComprobanteTipo());
    }

    @Override
    @Transactional
    public PagoDTO aprobar(Long id, AprobacionPagoDTO dto) {
        Pago p = buscarPendiente(id);
        Usuario u = p.getUsuario();
        // P-02: el periodo se recalcula con la fecha de aprobación (continúa si el último aprobado está en gracia).
        Calculo calculo = calcular(u, LocalDate.now(clock));
        p.setTarifa(calculo.tarifa());
        p.setPeriodoInicio(calculo.periodo().inicio());
        p.setPeriodoFin(calculo.periodo().fin());
        p.setMontoEsperado(calculo.tarifa().getValorMensual());
        p.setMontoConfirmado(dto.montoConfirmado());
        if (dto.observacion() != null && !dto.observacion().isBlank()) {
            p.setObservacion(dto.observacion().trim());
        }
        p.setEstado(EstadoPago.APROBADO);
        p.setRevisadoEn(clock.instant());
        pagoRepo.saveAndFlush(p);
        if (!u.isDadoDeBaja()) {
            usuarioService.recalcularEstado(u);
        }
        return mapeos.pago(p);
    }

    @Override
    @Transactional
    public PagoDTO rechazar(Long id, RechazoPagoDTO dto) {
        Pago p = buscarPendiente(id);
        p.setEstado(EstadoPago.RECHAZADO);
        p.setMotivoRechazo(dto.motivo().trim());
        p.setRevisadoEn(clock.instant());
        pagoRepo.saveAndFlush(p);
        return mapeos.pago(p);
    }

    // ---------------------------------------------------------------------------------------------------------

    private record Calculo(CalculadoraPeriodo.Periodo periodo, Tarifa tarifa) {
    }

    private record Archivo(byte[] bytes, String tipo, String extension, String sha256) {
    }

    /** Periodo (RF-27) y tarifa vigente en su inicio para el tipo del cupo (o del vehículo si no tiene cupo). */
    private Calculo calcular(Usuario u, LocalDate referencia) {
        LocalDate ultimoFin = mapeos.vigenteHasta(u.getId());
        CalculadoraPeriodo.Periodo periodo = CalculadoraPeriodo.calcular(ultimoFin, referencia, diasGracia);
        TipoVehiculo tipo;
        if (u.getCupo() != null) {
            tipo = u.getCupo().getTipoVehiculo();
        } else {
            Vehiculo v = mapeos.vehiculoActivo(u);
            if (v == null) {
                throw NegocioException.conflicto("El usuario no tiene cupo ni vehículo asignado");
            }
            tipo = v.getTipoVehiculo();
        }
        return new Calculo(periodo, tarifaService.vigenteEn(tipo, periodo.inicio()));
    }

    private PagoDTO guardarConArchivo(Pago p, Archivo archivo, LocalDate hoy) {
        String ruta = null;
        if (archivo != null) {
            ruta = almacen.guardar(archivo.bytes(), archivo.extension(), hoy);
            p.setComprobanteRuta(ruta);
        }
        try {
            pagoRepo.saveAndFlush(p);
        } catch (RuntimeException e) {
            almacen.eliminar(ruta);
            throw e;
        }
        return mapeos.pago(p);
    }

    /** Tamaño y tipo real por bytes mágicos (JPEG/PNG), nunca por la extensión ni el Content-Type del cliente. */
    private Archivo validarArchivo(MultipartFile f, boolean obligatorio) {
        if (f == null || f.isEmpty()) {
            if (obligatorio) {
                throw NegocioException.invalido("El comprobante es obligatorio");
            }
            return null;
        }
        if (f.getSize() > maxBytes) {
            throw NegocioException.demasiadoGrande("El archivo supera el tamaño máximo permitido (5 MB)");
        }
        byte[] bytes;
        try {
            bytes = f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String tipo;
        String ext;
        if (esJpeg(bytes)) {
            tipo = "image/jpeg";
            ext = "jpg";
        } else if (esPng(bytes)) {
            tipo = "image/png";
            ext = "png";
        } else {
            throw NegocioException.tipoNoSoportado("Solo se aceptan imágenes JPEG o PNG");
        }
        return new Archivo(bytes, tipo, ext, sha256(bytes));
    }

    private static boolean esJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean esPng(byte[] b) {
        int[] firma = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (b.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((b[i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Pago buscarVisible(Long id, UsuarioAutenticado quien) {
        Pago p = pagoRepo.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Pago no encontrado"));
        // IDOR: un usuario solo ve sus pagos; uno ajeno responde igual que uno inexistente.
        if (!quien.esAdmin() && !p.getUsuario().getId().equals(quien.id())) {
            throw NegocioException.noEncontrado("Pago no encontrado");
        }
        return p;
    }

    private Pago buscarPendiente(Long id) {
        Pago p = pagoRepo.findById(id).orElseThrow(() -> NegocioException.noEncontrado("Pago no encontrado"));
        if (p.getEstado() != EstadoPago.PENDIENTE) {
            throw NegocioException.conflicto("Solo se pueden revisar pagos pendientes");
        }
        return p;
    }
}
