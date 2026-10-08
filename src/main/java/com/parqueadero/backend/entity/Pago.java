package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Mensualidad con comprobante. Sustituye a la antigua entidad Payment. */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarifa_id")
    private Tarifa tarifa;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fin", nullable = false)
    private LocalDate periodoFin;

    @Column(name = "monto_esperado", nullable = false)
    private Integer montoEsperado;

    @Column(name = "monto_ocr")
    private Integer montoOcr;

    @Column(name = "fecha_pago_ocr")
    private LocalDate fechaPagoOcr;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_estado", nullable = false, length = 10)
    private OcrEstado ocrEstado;

    /** Solo los campos extraídos (fecha, valor); nunca el texto completo del comprobante. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_datos", columnDefinition = "jsonb")
    private Map<String, Object> ocrDatos;

    @Column(name = "monto_confirmado")
    private Integer montoConfirmado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    @Column(length = 200)
    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "registrado_por", nullable = false, length = 10)
    private RegistradoPor registradoPor;

    /** Ruta relativa a UPLOAD_DIR (nunca una URL). */
    @Column(name = "comprobante_ruta", length = 255)
    private String comprobanteRuta;

    @Column(name = "comprobante_tipo", length = 30)
    private String comprobanteTipo;

    @Column(name = "comprobante_sha256", length = 64)
    private String comprobanteSha256;

    @Column(name = "posible_duplicado", nullable = false)
    private boolean posibleDuplicado;

    @Column(name = "revisado_en")
    private Instant revisadoEn;

    @Column(nullable = false)
    private boolean simulado;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
