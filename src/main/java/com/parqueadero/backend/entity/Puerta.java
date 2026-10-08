package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Puerta simulada. Abierta = abiertaHasta posterior al instante actual (ADR 0004); no hay job de cierre. */
@Entity
@Table(name = "puerta")
@Getter
@Setter
@NoArgsConstructor
public class Puerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(name = "abierta_hasta")
    private Instant abiertaHasta;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public boolean estaAbierta(Instant ahora) {
        return abiertaHasta != null && abiertaHasta.isAfter(ahora);
    }
}
