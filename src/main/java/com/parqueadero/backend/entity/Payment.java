package com.parqueadero.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue
    private Long id;

    private Long userId; 
    private LocalDate paymentDate; 
    private LocalDate serviceStart; 
    private LocalDate serviceEnd;

    @ElementCollection 
    @CollectionTable(name = "payment_data")
    @MapKeyColumn(name = "field")
    @Column(name = "value")
    private Map<String, String> ocrData;
    @Column(length = 20)
    private String placa;
    private String imagePath;
    private Long amount; 


}
