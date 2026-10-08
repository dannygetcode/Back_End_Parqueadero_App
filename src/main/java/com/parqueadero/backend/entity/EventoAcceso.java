package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Cada lectura de placa o apertura, permitida o denegada (ADR 0004). Fuente de verdad de la ocupación. */
@Entity
@Table(name = "evento_acceso")
@Getter
@Setter
@NoArgsConstructor
public class EventoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @Column(name = "placa_leida", nullable = false, length = 15)
    private String placaLeida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camara_id")
    private Camara camara;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puerta_id")
    private Puerta puerta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private TipoEvento tipo;

    @Column(name = "tipo_inferido", nullable = false)
    private boolean tipoInferido = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private ResultadoAcceso resultado;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    private MotivoAcceso motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrigenAcceso origen;

    @Column(length = 200)
    private String observacion;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @CreationTimestamp
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    @Column(nullable = false)
    private boolean simulado;
}
