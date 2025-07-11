package com.parqueadero.backend.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.Map;

@Data
public class PaymentDTO {
    private Long id;
    private Long userId;
    private LocalDate paymentDate;
    private LocalDate serviceStart;
    private LocalDate serviceEnd;
    private Map<String, String> ocrData;
    private String placa;
    private String imageUrl;
    private Long amount;
}

