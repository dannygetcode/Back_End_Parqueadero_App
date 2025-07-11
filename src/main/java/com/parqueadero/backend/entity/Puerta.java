package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "puerta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Puerta {
    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Boolean abierta;
}
