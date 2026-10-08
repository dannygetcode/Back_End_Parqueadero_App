package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Plaza física. La capacidad del parqueadero es el número de cupos activos por tipo. */
@Entity
@Table(name = "cupo")
@Getter
@Setter
@NoArgsConstructor
public class Cupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vehiculo", nullable = false, length = 10)
    private TipoVehiculo tipoVehiculo;

    @Column(nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
