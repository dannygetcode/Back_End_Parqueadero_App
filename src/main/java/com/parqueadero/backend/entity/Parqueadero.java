package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** La sede. Hoy hay una sola fila (id = 1); el resto de tablas raíz cuelgan de ella. */
@Entity
@Table(name = "parqueadero")
@Getter
@Setter
@NoArgsConstructor
public class Parqueadero {

    /** Id de la única sede de la Fase 1 (sembrada en V2). */
    public static final long PRINCIPAL = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "zona_horaria", nullable = false, length = 40)
    private String zonaHoraria;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
