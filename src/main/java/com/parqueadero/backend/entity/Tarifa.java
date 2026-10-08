package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/** Precio mensual por tipo. La vigente en una fecha F es la de mayor vigenteDesde <= F. */
@Entity
@Table(name = "tarifa")
@Getter
@Setter
@NoArgsConstructor
public class Tarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false, length = 10)
    private TipoVehiculo tipoVehiculo;

    /** Pesos colombianos. */
    @Column(name = "valor_mensual", nullable = false)
    private Integer valorMensual;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
