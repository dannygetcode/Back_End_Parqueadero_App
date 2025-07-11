package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "camaras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camara {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String url;      //https://192.168.1.6:8080/

    @Column(nullable = false)
    private Boolean activa;
}
