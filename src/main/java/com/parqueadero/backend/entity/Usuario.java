package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Cliente mensual. PIN y código de validación solo como hash BCrypt; nunca salen por la API.
 * Sin @Data a propósito: equals/hashCode/toString recursivos con relaciones LAZY.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parqueadero_id")
    private Parqueadero parqueadero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cupo_id")
    private Cupo cupo;

    @Column(nullable = false, length = 15)
    private String telefono;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String apellido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoUsuario estado = EstadoUsuario.VENCIDO;

    @Column(name = "suspendido_motivo", length = 200)
    private String suspendidoMotivo;

    @Column(name = "pin_hash", length = 60)
    private String pinHash;

    @Column(name = "codigo_validacion_hash", length = 60)
    private String codigoValidacionHash;

    @Column(name = "codigo_validacion_expira_en")
    private Instant codigoValidacionExpiraEn;

    @Column(name = "validado_en")
    private Instant validadoEn;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "consentimiento_datos_en")
    private Instant consentimientoDatosEn;

    @Column(name = "consentimiento_version", length = 20)
    private String consentimientoVersion;

    @Column(nullable = false)
    private boolean simulado;

    @Column(name = "dado_de_baja_en")
    private Instant dadoDeBajaEn;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    public boolean isDadoDeBaja() {
        return dadoDeBajaEn != null;
    }

    public boolean isValidado() {
        return validadoEn != null;
    }
}
