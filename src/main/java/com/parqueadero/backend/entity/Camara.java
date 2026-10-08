package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "camara")
@Getter
@Setter
@NoArgsConstructor
public class Camara {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(length = 200)
    private String url;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(nullable = false)
    private boolean simulada = true;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;
}
