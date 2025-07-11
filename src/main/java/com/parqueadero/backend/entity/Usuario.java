package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 15)
    private String telefono;

    @Column(nullable = false)
    private Boolean verificado;

    @Column(nullable = false, length = 8)
    private String codigoValidacion;

    @Column(nullable = true, length = 4)
    private String pin;

    @Column(nullable = false)
    private Boolean activo;

    @Column(length = 50)
    private String nombre;

    @Column(length = 50)
    private String apellido;

    @Column(length = 10, unique = true)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoUsuario estado = EstadoUsuario.VENCIDO;

}
